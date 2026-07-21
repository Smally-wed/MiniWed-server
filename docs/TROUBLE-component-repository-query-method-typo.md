# ComponentRepository 쿼리 메서드 오타로 애플리케이션 기동 실패

- 발생일: 2026-07-17
- 영향 범위: **애플리케이션 전체 기동 불가**(Spring ApplicationContext 로드 실패). 커밋 `3117208`/`b3e051e`(계획 A′) 이후의 모든 실행.
- 심각도: 긴급

## 증상

계획 C(Template 레시피 전환) 작업 중 깨져 있던 `compileTestJava`를 복구하자, 그동안 **한 번도 실행되지 않던** 테스트 스위트가 처음 돌면서 `ServerApplicationTests > contextLoads()`가 실패했다.

```
java.lang.IllegalStateException: Failed to load ApplicationContext
Caused by: UnsatisfiedDependencyException: Error creating bean with name 'componentController'
Caused by: UnsatisfiedDependencyException: Error creating bean with name 'componentServiceImpl'
Caused by: BeanCreationException: Error creating bean with name 'componentRepository'
Caused by: QueryCreationException: Cannot create query for method
           [ComponentRepository.findbyComponentUid(java.lang.String)];
           No property 'findbyComponentUid' found for type 'Component'
Caused by: PropertyReferenceException: No property 'findbyComponentUid' found for type 'Component'
```

즉 단위 테스트 몇 개가 빨간 수준이 아니라 **서버가 뜨지 않는 상태**였다.

## 원인 분석

1. **가설 1: 로컬 인프라(Postgres/Redis) 부재로 인한 환경 문제.**
   `contextLoads()` 실패는 대개 DB/Redis 연결 문제라 여기부터 의심했다.
   → **기각.** 스택트레이스의 `Caused by` 최하단이 `Connection refused`가 아니라 `PropertyReferenceException`이었다. 연결이 아니라 **쿼리 메서드 이름 파싱** 단계에서 터졌다.

2. **가설 2: 계획 C의 Template 리팩터가 뭔가를 깨뜨렸다.**
   마침 `Template`/`TemplateServiceImpl`을 대대적으로 갈아엎던 중이라 자연스러운 의심이었다.
   → **기각.** 실패 빈은 전부 `component*`(`componentController` → `componentServiceImpl` → `componentRepository`)로, Template 도메인과 무관했다. `git log`상 `ComponentRepository`는 커밋 `3117208`에서 들어온 뒤 이번 세션에서 건드린 적이 없다.

3. **가설 3(적중): Spring Data JPA 쿼리 메서드 이름 오타.**
   `ComponentRepository.findbyComponentUid(String)` — 주어 키워드가 `findBy`가 아니라 **`findby`**(소문자 b)였다.
   Spring Data는 메서드 이름을 `find` + `By` + 프로퍼티로 파싱하는데, `findby...`는 `By` 구분자가 없으므로 메서드 이름 **전체**를 하나의 프로퍼티 이름(`findbyComponentUid`)으로 간주한다. `Component` 엔티티에 그런 프로퍼티가 없으니 `PropertyReferenceException` → 리포지토리 빈 생성 실패 → 컨텍스트 로드 실패 → **기동 불가**.

**왜 여태 아무도 몰랐는가 (근본 원인보다 중요한 부분):**
`compileTestJava`가 계속 깨져 있었다(옛 모델을 참조하는 스테일 테스트 5개). Gradle은 `compileTestJava` 실패 시 `test` 태스크에 도달하지 못하므로 `contextLoads()`가 **한 번도 실행되지 않았다**. 컴파일 에러가 기동 불가 버그를 가리고 있었다. 이 오타는 컴파일 타임에는 잡히지 않는다(인터페이스 메서드 선언은 문법적으로 합법이고, 구현은 런타임에 Spring이 생성한다).

## 해결 방법

두 파일, 실질적으로 한 글자 수정:

- `src/main/java/smally/server/domain/component/repository/ComponentRepository.java`
  `findbyComponentUid(String componentUid)` → `findByComponentUId(String componentUId)`
  (`By`를 대문자로 고치고, 프로퍼티명을 엔티티 필드 `componentUId`와 **정확히** 일치시켰다. 필드가 `componentUId`(대문자 I)라 `findByComponentUid`로 쓰면 프로퍼티 해석이 어긋날 수 있어 철자를 그대로 맞췄다.)
- `src/main/java/smally/server/domain/component/service/ComponentServiceImpl.java`
  호출부 `componentRepository.findbyComponentUid(...)` → `findByComponentUId(...)`

수정 후 `contextLoads()` 통과 → 애플리케이션 기동 정상.

## 재발 방지

- **`ServerApplicationTests.contextLoads()`가 이제 실제로 돈다.** 이번 작업에서 스테일 테스트 5개를 정리해 `compileTestJava`를 green으로 만들었고(`clean build` 기준 59 tests / 0 failures), 그 결과 이 유형의 기동 실패는 앞으로 빌드에서 즉시 잡힌다. 사실상 이번 사건의 재발 방지 장치는 "테스트가 실행되는 상태를 유지하는 것" 그 자체다.
- **`ComponentServiceImplTest.getComponent_없는_componentUId면_COMPONENT_NOT_FOUND`**가 이제 `findByComponentUId`를 스텁한다. 종전에는 존재하지도 않는 경로(`findById(999L)`)를 스텁해 `UnnecessaryStubbingException`으로 실패했고, 리포지토리 메서드가 잘못돼도 알려주지 못했다.
- **교훈(문서화 목적)**: 컴파일 에러를 오래 방치하면 그 뒤의 런타임 회귀가 통째로 가려진다. 스테일 테스트는 "나중에 고칠 것"이 아니라 **탐지 능력의 상실**로 취급해야 한다.
- 미도입: Spring Data 쿼리 메서드 이름을 정적으로 검증하는 장치는 없다(리포지토리 슬라이스 테스트 `@DataJpaTest`를 도입하면 DB 없이도 파생 쿼리 파싱을 검증할 수 있으나, 이번 범위 밖 — 후속 과제).

## 참고

- 계획: [docs/superpowers/plans/2026-07-17-template-recipe.md](./superpowers/plans/2026-07-17-template-recipe.md) — 이 버그를 발견한 작업(계획 C). Task 0이 빌드 복구를 담당.
- ADR: [ADR-007](./adr/ADR-007-section-component-template-model.md) — `componentUId`를 컴포넌트의 외부 식별자로 정한 결정(결정 1).
- 유입 커밋: `3117208` (feat : component CRUD 작성), `b3e051e` (feat : component 관리를 위한 componentType 제작).
- Spring Data JPA — Defining Query Methods(주어 키워드 `findBy`/`readBy`/`getBy` 파싱 규칙).
