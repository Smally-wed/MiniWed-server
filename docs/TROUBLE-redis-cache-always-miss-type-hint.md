# Redis 캐시가 항상 cache miss 나는 문제 (타입 힌트 미저장)

- 발생일: 2026-07-20
- 영향 범위: Redis 캐시를 사용하는 전 구간 (컴포넌트 단일/전체 조회, 컴포넌트 타입 목록, 템플릿 조회)
- 심각도: 높음

## 증상

Redis에 값은 정상적으로 저장되는데, 조회할 때마다 cache miss가 발생해 캐시가 전혀 동작하지 않았다.

`RedisCacheService.getCacheData()`에서 찍은 로그:

```
{id=1, name=morden-title, componentType=인삿말, frontendBinding=3b0d850e-7d30-40e0-a559-ee78963853bb, optionSchema={type=object, properties={fontSize={enum=[sm, md, lg], type=string, default=md}}}}
2026-07-20T20:14:59.767+09:00  WARN 79854 --- [server] [nio-8080-exec-2] s.s.d.c.s.ComponentServiceImpl : [Cache-miss] component detail cache miss key : 3b0d850e-7d30-40e0-a559-ee78963853bb
```

값 자체는 읽혔는데 바로 다음 줄에서 cache miss 로그가 찍혔다.

## 원인 분석

### 가설 1: 로그에 찍힌 값의 타입이 `ComponentResponse`가 아니다 → 확인됨

`ComponentResponse`는 record라 `toString()`이 `ComponentResponse[id=1, ...]` 형태여야 하는데,
로그는 `{id=1, ...}` 형태였다. 즉 `LinkedHashMap`으로 복원된 것이다.

그래서 아래 코드의 `type.isInstance(value)`가 false가 되어 `null`을 반환했고, 호출부는 이를 cache miss로 처리했다.

```java
return type.isInstance(value) ? type.cast(value) : null;
```

### 가설 2: 직렬화기가 타입 정보(`@class`)를 저장하지 않는다 → 확인됨 (근본 원인)

`RedisConfig`에서 직렬화기를 이렇게 만들고 있었다.

```java
GenericJacksonJsonRedisSerializer jsonSerializer = GenericJacksonJsonRedisSerializer.builder().build();
```

spring-data-redis 4.1.0 소스를 열어 확인한 결과, 빌더의 기본값이 다음과 같았다.

```java
private boolean defaultTyping = false;
```

즉 `enableDefaultTyping(...)` 또는 `enableUnsafeDefaultTyping()`을 호출하지 않으면 `@class` 타입 힌트를 저장하지 않는다.
`RedisTemplate<String, Object>`이므로 역직렬화 대상 타입은 `Object.class`이고, 타입 힌트가 없으니 Jackson은 JSON object를 `LinkedHashMap`으로 복원할 수밖에 없었다.

### 부수적으로 발견한 잠재 버그

`ComponentServiceImpl.getAllComponents()`는 `List.class`로 캐시를 조회한다.
리스트는 `type.isInstance(value)`를 **통과**하기 때문에 cache miss가 나지 않고,
`List<LinkedHashMap>`이 `List<ComponentResponse>`인 것처럼 반환되어 원소를 사용할 때 `ClassCastException`이 나는 상태였다.
단일 조회보다 오히려 더 위험한 상태였다.

### 막다른 길: 테스트에서 `List.of(...)` 사용

수정 후 검증 테스트를 작성할 때 리스트 케이스만 실패했다.

```
MismatchedInputException: Unexpected token (`JsonToken.START_OBJECT`), expected `JsonToken.VALUE_STRING`:
need String, Number of Boolean value that contains type id (for subtype of java.lang.Object)
```

처음엔 수정이 잘못된 줄 알았으나, 원인은 테스트 코드가 production과 달랐던 것이었다.
`GenericJacksonJsonRedisSerializer.TypeResolverBuilder.useForType()`은 **java 패키지의 final 클래스에는 타입 힌트를 붙이지 않는다.**

```java
if (javaType.isFinal() && !KotlinDetector.isKotlinType(javaType.getRawClass())
        && javaType.getRawClass().getPackageName().startsWith("java")) {
    return false;
}
```

`List.of(...)`가 만드는 `java.util.ImmutableCollections$ListN`이 여기 해당한다.
production 코드는 `new ArrayList<>(responses)`로 저장하고 있어(`ArrayList`는 final이 아님) 실제로는 문제가 없었다.
테스트를 production과 동일하게 `ArrayList`로 맞추자 통과했다.

**주의: 앞으로 리스트를 캐시에 저장할 때는 반드시 `ArrayList` 등 non-final 타입으로 감싸야 한다.**

## 해결 방법

### 1. `src/main/java/smally/server/config/RedisConfig.java`

타입 힌트 저장을 활성화했다. 임의 클래스 역직렬화를 막기 위해 허용 패키지를 제한하는
`PolymorphicTypeValidator`를 함께 지정했다(`enableUnsafeDefaultTyping()`은 사용하지 않음).

```java
GenericJacksonJsonRedisSerializer jsonSerializer = GenericJacksonJsonRedisSerializer.builder()
        .enableDefaultTyping(cacheTypeValidator())
        .build();

private PolymorphicTypeValidator cacheTypeValidator() {
    return BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("smally.server.")
            .allowIfSubType("java.util.")
            .allowIfSubType("java.lang.")
            .allowIfSubType("java.time.")
            .build();
}
```

### 2. `src/main/java/smally/server/core/cache/RedisCacheService.java`

디버깅용으로 넣었던 `log.info(value.toString())`을 정리했다.
이 코드는 캐시 미스일 때 `value`가 null이라 NPE를 던지고, 그게 catch에 잡혀 실제 원인이 가려지는 구조이기도 했다.
null과 타입 불일치를 구분해 처리하도록 바꿨다.

```java
Object value = redisTemplate.opsForValue().get(key);
if (value == null) {
    return null;
}
if (!type.isInstance(value)) {
    log.warn("[Cache] type mismatch key : {}, expected : {}, actual : {}",
            key, type.getSimpleName(), value.getClass().getName());
    return null;
}
return type.cast(value);
```

## 재발 방지

- `src/test/java/smally/server/config/RedisConfigTest.java` 추가.
  `RedisConfig`가 만든 직렬화기로 `ComponentResponse` 단일 객체와 리스트를 round-trip 하여
  원래 타입으로 복원되는지 검증한다. 타입 힌트가 꺼지면 이 테스트가 실패한다.
- 리스트를 `ArrayList`로 감싸야 하는 이유를 테스트에 주석으로 남겼다.
- 타입 불일치 시 기대 타입과 실제 타입을 함께 로그로 남기도록 해, 같은 문제가 생기면 즉시 드러나게 했다.

## 참고

- 기존에 저장된 캐시 값은 `@class` 필드가 없어 역직렬화에 실패한다.
  `RedisCacheService.getCacheData()`의 catch에 걸려 null(cache miss)을 반환하고
  곧바로 `setCacheData()`로 새 형식이 덮어써지므로 자연히 해소되지만,
  배포 직후 에러 로그가 한 차례 늘어난다. 원하면 배포 시 해당 키를 미리 비워도 된다.
- spring-data-redis 4.1.0 `GenericJacksonJsonRedisSerializer` 소스 (빌더 기본값 및 `useForType` 로직)
