# Spring 의존성 주입(DI)과 Redis 캐시 서비스

- 작성일: 2026-07-17
- 대상: 서버 개발자
- 관련 코드: `config/RedisConfig.java`, `core/cache/RedisCacheService.java`

`RedisConfig`에서 등록한 `RedisTemplate` 빈을 공통 캐시 서비스에서 어떻게 주입해 쓰는지,
그리고 그 과정에서 헷갈리는 개념과 자주 겪는 문제를 정리한 문서다.

---

## 1. 핵심 개념

### 빈(Bean)
Spring이 직접 생성하고 생명주기를 관리하는 객체다. 개발자가 `new`로 만들지 않고,
Spring 컨테이너(ApplicationContext)가 만들어 보관한다.

빈을 등록하는 방법은 두 가지다.

- **`@Bean` (메서드 등록)**: 설정 클래스(`@Configuration`)의 메서드가 리턴하는 객체를 빈으로 등록한다.
  라이브러리 객체처럼 내가 세부 설정을 손봐야 할 때 쓴다. → `RedisConfig`의 `redisTemplate()`.
- **`@Component` 계열 (클래스 등록)**: 클래스에 `@Component`, `@Service`, `@Repository`,
  `@Controller`를 붙이면 컴포넌트 스캔으로 자동 등록된다. → `RedisCacheService`.

### 의존성 주입(Dependency Injection, DI)
어떤 객체가 필요로 하는 다른 객체(의존성)를, 그 객체가 직접 만들지 않고
Spring이 대신 넣어주는 것.

```java
// DI 없이 (강한 결합, 직접 생성)
RedisTemplate template = new RedisTemplate();   // 설정도 내가 다 해야 함

// DI 사용 (Spring이 이미 설정된 빈을 넣어줌)
public RedisCacheService(RedisTemplate<String, Object> redisTemplate) { ... }
```

### 제어의 역전(IoC)
"객체를 만들고 연결하는 책임"을 개발자가 아니라 Spring 컨테이너가 가져가는 것.
DI는 IoC를 구현하는 대표적인 방법이다.

---

## 2. 이 프로젝트에서의 흐름

```
RedisConfig.redisTemplate()   ──@Bean 등록──▶  컨테이너에 RedisTemplate<String,Object> 보관
                                                      │
                                                      │ 타입으로 찾아 주입
                                                      ▼
RedisCacheService  ──@Component로 빈 등록──▶  생성자 파라미터 RedisTemplate 자리에 주입
```

### RedisCacheService 코드

```java
@Component                        // 이 클래스도 빈으로 등록
@RequiredArgsConstructor          // final 필드를 받는 생성자를 Lombok이 자동 생성
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;   // 주입 대상
}
```

`@RequiredArgsConstructor`가 아래 생성자를 자동으로 만들어준다.

```java
public RedisCacheService(RedisTemplate<String, Object> redisTemplate) {
    this.redisTemplate = redisTemplate;
}
```

Spring은 이 생성자를 보고 "`RedisTemplate` 타입 빈이 필요하구나" 판단해
`RedisConfig`가 등록한 빈을 찾아 넣는다. **따로 `@Autowired`를 붙이지 않아도 된다**
(생성자가 하나뿐이면 Spring이 자동으로 그 생성자를 주입에 사용한다).

---

## 3. 주입 방식 3가지와 권장안

| 방식 | 예시 | 평가 |
|------|------|------|
| **생성자 주입** | `@RequiredArgsConstructor` + `private final` | ✅ 권장. 불변성 보장, 테스트 시 직접 주입 가능, 순환참조를 컴파일/기동 시점에 발견 |
| 필드 주입 | `@Autowired private RedisTemplate ...;` | ❌ 비권장. 테스트에서 목 주입이 어렵고 `final` 불가 |
| Setter 주입 | `@Autowired public void setX(...)` | 선택적(없어도 되는) 의존성에만 |

→ 이 프로젝트는 **생성자 주입**을 기본으로 한다. 지금 `RedisCacheService`가 쓰는 방식이 정석이다.

---

## 4. 트러블슈팅 (자주 겪는 문제)

### 4-1. raw 타입 경고 / 타입 안정성 상실
**증상**: `private final RedisTemplate redisTemplate;` 처럼 제네릭을 생략하면
`unchecked` 경고가 뜨고, `opsForValue().get()` 반환 타입이 `Object`라 매번 캐스팅해야 한다.

**해결**: 등록한 빈과 동일하게 제네릭을 명시한다.
```java
private final RedisTemplate<String, Object> redisTemplate;   // ✅
```
빈 등록부(`RedisConfig`)와 주입부의 타입 파라미터를 일치시킨다.

### 4-2. `NoSuchBeanDefinitionException: RedisTemplate`
**증상**: 기동 시 "필요한 타입의 빈이 없다"는 예외.

**원인 후보**
- `RedisConfig`에 `@Configuration`이 빠졌거나, 컴포넌트 스캔 범위(`smally.server` 하위) 밖에 있음.
- `@Bean` 메서드 이름/리턴 타입 문제로 원하는 빈이 안 만들어짐.

**해결**: `RedisConfig`가 `@Configuration`이고 `smally.server` 패키지 하위에 있는지,
`@Bean public RedisTemplate<String,Object> redisTemplate(...)`가 실제로 존재하는지 확인한다.

### 4-3. `NoUniqueBeanDefinitionException` (같은 타입 빈이 여러 개)
**증상**: `RedisTemplate` 타입 빈이 2개 이상이라 어느 걸 주입할지 못 정함.

**해결**: 빈에 이름을 주고 주입부에서 `@Qualifier("이름")`로 지정하거나,
주로 쓰는 빈에 `@Primary`를 붙인다.
```java
@Bean @Primary
public RedisTemplate<String, Object> redisTemplate(...) { ... }
```

### 4-4. `@RequiredArgsConstructor`인데 주입이 안 됨 (NPE)
**증상**: 필드가 `null`.

**원인 후보**
- 필드에 `final`이 없어서 `@RequiredArgsConstructor` 생성자 파라미터에 포함되지 않음.
- 클래스에 `@Component`(또는 `@Service` 등)가 없어 빈으로 등록되지 않음
  → 그 클래스를 `new`로 직접 만들면 Spring이 주입할 수 없다. 반드시 주입받아 쓴다.
- Lombok 애노테이션 프로세싱이 꺼져 생성자가 생성되지 않음(IDE 설정).

**해결**: 필드를 `final`로, 클래스에 `@Component` 계열 애노테이션을 확인한다.

### 4-5. 직렬화 관련 오류 / 값이 이상하게 저장됨
**증상**: Redis에 저장된 키가 `\xac\xed...` 같은 바이너리로 보이거나,
읽을 때 역직렬화 예외가 난다.

**원인**: 기본 `RedisTemplate`은 JDK 직렬화를 쓴다.
**해결**: `RedisConfig`처럼 Key는 `StringRedisSerializer`, Value는
`GenericJacksonJsonRedisSerializer`로 지정한다(현재 설정에 이미 반영됨).

### 4-6. 기동/런타임 시 Redis 연결 실패
**증상**: `Unable to connect to Redis` / `Connection refused`.

**원인**: 로컬에 Redis가 안 떠 있거나 `spring.data.redis.host/port` 설정이 다름.
**해결**: Redis 컨테이너/서비스가 실행 중인지, application 설정의 host·port가 맞는지 확인한다.
(이 프로젝트는 빌드 훅이 Postgres/Redis 실행을 요구하므로 로컬 인프라를 먼저 띄운다.)

---

## 5. 다음 단계 (공통 캐시 서비스 확장)
현재 `RedisCacheService`는 빈만 주입된 골격 상태다. 공통 캐시 서비스로 쓰려면
`get`, `set(TTL 포함)`, `delete`, `hasKey` 같은 메서드를 추가하면 된다.
이 부분은 별도 작업으로 진행한다.

---

## 참고
- Spring `RedisTemplate` / `ValueOperations` 문서
- 관련 설정: `config/RedisConfig.java`
