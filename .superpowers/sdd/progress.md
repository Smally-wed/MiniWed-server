# ADR-007 하위 계획 진행 기록

- 실행 방식: **커밋 금지** (사용자 최종 검토 후 직접 커밋)
- 계획 A′(Component/ComponentType): **커밋됨** (`3117208`, `b3e051e`)
- 계획 B(OptionDefinition): 작업 트리, 미커밋
- 계획 C(Template 레시피): 작업 트리, 미커밋 ← 이번 세션

---

## 계획 B — OptionDefinition 마스터 옵션 카탈로그 (2026-07-16)

- 계획: docs/superpowers/plans/2026-07-16-option-definition-catalog.md
- [x] Task 1: OptionDefinition 엔티티 + DTO
- [x] Task 2: Repository + ErrorCode/Exception + Service
- [x] Task 3: Response + Controller

### 최종 리뷰
- 전체 리뷰(opus): 머지 준비됨. Critical 0. 테스트 7/7. 12파일(미커밋).
- Important 수정: defaultValue=null인데 allowedValues 있으면 INVALID_OPTION_DEFAULT로 오거절 → `defaultValue != null &&` 가드 추가. 테스트 2개 추가 → 5→7 통과.
  - 결정: defaultValue는 선택. null이면 기본값검증 스킵.
- Important(기존/상속): permitAll 미인증 쓰기 — 인가 패스에서 조임.
- Minor: 201/200 앱 관례.

---

## 계획 C — Template 레시피 전환 (2026-07-17)

- 계획: docs/superpowers/plans/2026-07-17-template-recipe.md (**개정 1 + 실행 완료**)
- 착수 전 계획서를 실제 코드와 대조해 **재작성**했다(초안의 틀린 전제 3개 + 사용자 정책 결정 3개 반영).

### 사용자 정책 결정 (2026-07-17)
1. **Template 조회에 Redis 캐싱 적용** — Component와 동일 패턴(`TPL:` 키, TTL 30일).
2. **레시피 전환 원안대로** — `sectionSchema`·`variants`·`VariantResponse`·`InternalTemplateService` 전부 제거.
3. **Component 테스트 수정 + `domain/component` 패키지로 이동**.

### 태스크
- [x] Task 0: 빌드 복구 — Component 테스트 4개 이동/수정 (계획 개정 시 신설)
- [x] Task 1: Template 레시피 리팩터 (엔티티·DTO·서비스·컨트롤러·ErrorCode + 스테일 테스트 3개 삭제)
- [x] Task 2: 새 테스트 (TemplateCreateRequest 1, TemplateResponse 1, TemplateServiceImpl 10)

### 결과
- **`./gradlew clean build` → BUILD SUCCESSFUL, 59 tests / 0 failures.** (이 브랜치에서 테스트 스위트가 처음으로 전부 실행됐다.)
- Template = `sections`(List<Map> jsonb) + `theme`(Map jsonb) + 메타. 등록 시 레시피 유효성(componentUId 존재) + theme 허용값 검증. 조회는 `TPL:` 캐시 우선.
- API 변경: `GET /api/template/v1/variant/{uid}`(VariantResponse) → **`GET /api/template/v1/{uid}`(TemplateResponse 전체 상세)**. ⚠️ 프론트 영향.

### 🔴 실행 중 발견 — 기동 불가 버그 (계획 범위 밖, 수정함)
- `ComponentRepository.findbyComponentUid`(소문자 `b`) → Spring Data 파싱 실패 → **ApplicationContext 로드 실패 = 서버가 뜨지 않는 상태**였다. 커밋 `3117208` 이후 계속 존재했으나 `compileTestJava`가 깨져 `contextLoads()`가 한 번도 실행되지 않아 가려져 있었다.
- 계획 C의 `validateRecipe`가 이 메서드에 직접 의존하므로 수정: `findByComponentUId`(+ 호출부).
- 기록: docs/TROUBLE-component-repository-query-method-typo.md

### 사용자 확인 필요
1. **캐싱 vs 성능 연구 충돌** — 성능 연구 계획(2026-07-17-performance-research.md)의 연구 C가 *"이 규모에서 캐시가 애초에 이득인가"*를 아직 안 닫았는데, Component·Template 캐싱이 그 결론을 앞질러 들어갔다. 연구 C 결과에 따라 되돌릴 수 있음을 전제로 진행.
2. **캐시 무효화 부재** — 템플릿 수정/삭제가 범위 밖이라 등록 시 적재만 있고 무효화 경로가 없다. 수정 기능 추가 시 **필수**(성능 연구 탈락 조건 "stale read 0건"과 직결).
3. **A′ 프로덕션 코드를 수정했다** — `ComponentRepository`/`ComponentServiceImpl`. 커밋된 코드라 별도 커밋 분리를 권장.

### 후속 정리 (미착수)
- 미사용 `INVALID_SECTION_VALUES` 제거.
- `ComponentResponse.frontendBinding` 네이밍 통일(값은 `componentUId`, 레시피 JSON 키도 `componentUId` — 한 개념에 이름이 둘).
- `Component.@Builder`에 `componentType` 부재 → 빌드 후 `@Setter` 주입하는 2단 구조(타입 없는 Component 생성 가능).
- `@DataJpaTest` 리포지토리 슬라이스 도입 시 파생 쿼리 오타를 DB 없이 조기 검출 가능.
- **다음: 계획 D** — Invitation 저장 시 `templateUid` → 각 섹션 `componentUId` → `Component.data_schema` → `SchemaValidator.validateData`로 `section_values` 검증 + `selected_options` 검증.
