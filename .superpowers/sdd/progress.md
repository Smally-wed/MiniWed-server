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

---

## 계획 E — Template 썸네일 S3 업로드 (2026-07-18)

- 계획: docs/superpowers/plans/2026-07-18-template-thumbnail-s3.md
- 설계: docs/superpowers/specs/2026-07-18-template-thumbnail-s3-design.md
- ADR: docs/adr/ADR-008-template-thumbnail-server-relay-upload.md (승인됨)
- 실행 방식: **커밋 금지** (기존 원장 정책 유지, 사용자 최종 검토 후 직접 커밋). 서브에이전트 구현+테스트, 커밋 안 함. 리뷰는 작업 트리 diff.
- 기준 HEAD: `7e48e8b` (작업 트리에 계획 B/C/D 미커밋 변경 존재 — S3 무관 파일은 건드리지 않음)

### 태스크
- [x] Task 1: AWS SDK 의존성 + S3Properties/S3Config (리뷰 clean, BOM 2.48.2, 미커밋)
- [x] Task 2: ErrorCode + ImageException + StorageService/S3StorageServiceImpl (리뷰 clean, 4/4 통과, 미커밋)
- [x] Task 3: TemplateCreateRequest thumbnail 제거 + TemplateResponse.withThumbnail (리뷰 clean, DTO 코드 정상, test green은 Task 4와 함께, 미커밋)
- [x] Task 4: TemplateService 배선 (업로드→키 저장, 조회 presigned 주입) (리뷰 clean, 미커밋)
- [x] Task 5: 컨트롤러 multipart 수신 (compile OK, image.*+template.* 26/26 통과, 미커밋)

### 최종 전체 리뷰 (opus)
- 핵심(캐시/presigned 미영속, 객체 키 저장, 에러 코드 라우팅, out-of-scope 준수) 정상·스펙 준수 확인.
- **차단 #1 (Important) — 수정 완료:** Spring 기본 multipart 한도 1MB가 앱 5MB 검증보다 먼저 1~5MB 파일을 500으로 거부. 수정: main/test yml에 `spring.servlet.multipart.max-file-size=5MB, max-request-size=10MB` 추가 + `GlobalExceptionHandler`에 `MaxUploadSizeExceededException → IMAGE_TOO_LARGE(400)` 핸들러. compile OK, 단위 테스트 통과.
- **미해결 Minor (사용자 판단 대기):**
  - #2 **수정 완료:** `GlobalExceptionHandler`에 `MissingServletRequestPartException → 400`("필수 요청 파트가 누락되었습니다: {part}") 핸들러 추가.
  - #3 **수정 완료:** 파일명 기반 확장자 추출 제거. content-type 허용목록(`image/jpeg`→`.jpg`, `image/png`→`.png`)으로 검증·확장자 유도. 허용 외(예 image/gif) → INVALID_IMAGE_TYPE. 거부 테스트 추가. (jpg/jpeg=image/jpeg, png=image/png. webp는 보류.)
  - #4 **수정 완료:** `createTemplate`에서 클래스 `@Transactional` 제거 → S3 업로드가 트랜잭션 밖. save()는 리포지토리 자체 트랜잭션으로 저장. 검증→업로드→저장 순서 유지. (presignedGetUrl은 로컬 서명이라 getTemplate은 무관.) compile OK, TemplateServiceImplTest 통과.
  - 테스트: `upload_정상...`이 `verify(s3Client).putObject(...)` 미검증(no-op도 통과 가능).

### 상태
- **구현·검증 완료, 커밋 금지 정책으로 미커밋.** 사용자 최종 검토 후 직접 커밋 예정.

---

## 계획 F — 청첩장 저장 (Invitation Persistence) (2026-07-21)

- 계획: docs/superpowers/plans/2026-07-21-invitation-persistence.md
- 설계: docs/superpowers/specs/2026-07-21-invitation-persistence-design.md
- ADR: docs/adr/ADR-009-invitation-persistence-model.md (승인됨)
- 실행 방식: **커밋 금지** (사용자가 최종 검토 후 직접 커밋). 계획의 각 Task 커밋 스텝은 생략.
- 기준 HEAD: `d22928d` (문서 커밋까지 반영, 소스 작업 트리 깨끗)
- 리뷰 diff: 커밋이 없으므로 `git add -N` 후 `git diff HEAD -- <경로>`로 태스크별 추출

### 태스크
- [x] Task 1: Invitation 엔티티 + 리포지토리 + 에러코드 (5/5 통과, 빌드 성공, 리뷰 clean, 미커밋)
- [x] Task 2: SlugGenerator (4/4 통과, 리뷰 clean, 미커밋)
- [x] Task 3: SectionValueImageScanner (6/6 통과, 리뷰 clean, 불변성 확인됨, 미커밋)
- [x] Task 4: 템플릿 sectionId 필수화 + 컴포넌트 검증 null 가드 (신규 6/6, TemplateServiceImplTest 10/10 회귀 수정, 리뷰 승인, 미커밋)
- [x] Task 5: 이미지 업로드 기록/연결/고아 처리 (5/5 통과, 리뷰 승인, 권한·트랜잭션 경계 확인됨, 미커밋)
- [x] Task 6: InvitationValidator (9/9 통과, Important 2건 수정 후 재리뷰 승인, 미커밋)
- [x] Task 7: InvitationService (CRUD/발행) (7/7 통과, 회귀 16/16, 리뷰 승인, 미커밋)
- [x] Task 8: 하객 공개 조회 (4/4 통과, 리뷰 승인, DRAFT 은폐·캐시 불변 확인됨, 미커밋)
- [x] Task 9: 컨트롤러 + CurrentUser + 인가 규칙 (전체 120건 통과, 빌드 성공, 리뷰 승인, 미커밋)

### 확인 필요 (사용자)
- `ddl-auto: validate` + 마이그레이션 도구 없음(.sql/Flyway/Liquibase 부재) → 새 컬럼은 수동 DDL 필요.
  대상: `invitations.invitation_uid`, `invitations.selected_options`, `image_uploads.uploader_id`(NOT NULL — 기존 행 있으면 백필 필요).

### Minor 발견 (최종 리뷰에서 처리 여부 판단)
- Task 4-1: `TemplateRecipeValidationTest`에 공백 문자열/비문자열 `sectionId` 거부 테스트 없음 (구현은 정상, 회귀 커버리지 공백).
- Task 4-2: `ComponentDataValidationTest`가 `optionSchema`가 실재하는데 `optionData`가 null인 분기를 한 번도 실행하지 않음.
- Task 4 부수: `TemplateServiceImplTest`의 `componentUId 부재` 테스트가 sectionId 부재로 먼저 걸려 잘못된 이유로 통과하던 것을 수정함.
- Task 5 확인 필요(Task 7에서): `InvitationImageService.link()`가 다른 빈을 통해 호출되어 Spring 프록시를 타는지(더티 체킹이 트랜잭션 안에서 flush되려면 필요).
- Task 6 수정 내역: (1) selectedOptions 섹션값이 비-Map이면 INVALID_INVITATION_OPTIONS로 거부(이전엔 검증 통째 우회) + 회귀 테스트 2건, (2) "발행은 빈 섹션도 검증" 테스트를 verify로 실제 증명하도록 교체.
- Task 7 리뷰의 "범위 위반(validateRecipe에 sectionId 로직 추가)" 지적은 오탐. 미커밋 정책상 diff를 HEAD 기준으로 뽑아 Task 4의 승인된 변경이 같은 파일에 섞여 보인 것.
- Task 7 Minor(최종 리뷰에서 처리): `InvitationServiceImpl`에 미사용 import `Template` 잔존.
- Task 7 확인 완료: `invitationImageService.link()`를 주입받은 별도 빈으로 호출 → 프록시 정상 적용(Task 5 확인 항목 해소).
- Task 8 Important(최종 리뷰에서 처리): 옵션 병합 테스트가 결과값만 보고 "캐시 원본 불변"을 증명하지 않음 → in-place 수정으로 회귀해도 통과함. 회귀 방지 단언 추가 필요.
- **범위 밖 실질 이슈 (사용자 판단 필요):** S3 키가 `invitations/{내부 userId}/...` 라, 무인증 공개 조회가 내려주는 presigned URL 경로에 청첩장 소유자의 내부 PK가 평문 노출됨. 키 네이밍을 UUID 등으로 바꿀지 결정 필요.
- Task 9 Important(사용자 판단): 컨트롤러가 `ResponseEntity.ok(ApiResponse.of(CREATED, ...))` 형태라 바디의 status는 201/204인데 실제 HTTP 코드는 200. 기존 TemplateController/ComponentController와 같은 관행이나 AuthController는 실제 코드를 맞춤. 통일 여부 결정 필요.

### 전체 리뷰 결과 (opus)
- 종합: 수정 후 병합. Critical 2건 + G(DDL) 병합 전 필수, 나머지 후속.
- **Critical C-1:** delete()가 이미지 연결된 청첩장에서 FK 위반 → 500. 삭제 전 이미지 unlink 필요.
- **Critical C-2:** update()가 상태 무관하게 validateForDraft만 씀 → 발행본을 빈 값으로 덮어써 하객에게 깨진 청첩장 노출. 발행 상태면 validateForPublish 적용((a)안).
- Important I-1(다른 청첩장에 LINKED된 키 재연결 시 오배정), I-2(옵션병합 중복), I-3(unpublish 상태검증), I-4(create/delete 테스트 부재), I-5(느슨한 테스트 2건) → 후속/일부 지금.
- B 재정의: 진짜 위험은 캐시미스 시 영속 Template 엔티티 sections 제자리수정(더티체킹으로 DB 덮어씀). 현재 구현은 복사해서 안전.
- G: image_uploads.uploader_id NOT NULL은 기존 행 있으면 ALTER 실패 → 백필 포함 DDL 필요.

### Critical 수정 완료 (재리뷰 clean)
- C-1 막힘: ImageUpload.unlink()(참조 null+ORPHANED) + InvitationImageService.unlinkAll() + delete()가 삭제 전 호출. InOrder 테스트로 순서 검증.
- C-2 막힘: update()가 PUBLISHED면 validateForPublish, DRAFT면 validateForDraft로 분기. 상태별 validator 호출 테스트 추가.
- 전체 125건 통과, 빌드 성공. 새 문제 없음.
- G(DDL): docs/db/2026-07-21-invitation-persistence-ddl.sql 작성(백필 순서 포함).

### 최종 상태
- **9개 태스크 전부 구현·리뷰 완료 + Critical 2건 수정 완료. 전량 미커밋(사용자 최종 검토 후 직접 커밋).**
- 후속 처리 항목(사용자 판단): I-1(이미지 다중 청첩장 오배정), I-2(옵션병합 중복), I-3(unpublish 상태검증), C(HTTP 상태코드), D(S3키 내부PK 노출). 상세는 위 전체 리뷰 결과 참고.

### I-1, I-3 수정 완료 (사용자 요청, 미커밋)
- I-1: link()에서 이미 다른 청첩장에 연결된 키는 IMAGE_NOT_LINKABLE로 거부. ImageUpload.isLinkedToOtherThan(invitation) 추가(같은 청첩장 재저장은 허용). 테스트 2건(거부/멱등) 추가.
- I-3: unpublish()가 PUBLISHED 아니면 INVITATION_NOT_PUBLISHED로 거부(publish의 ALREADY_PUBLISHED와 대칭). ErrorCode 추가. 테스트 2건 추가.
- 부수: 사용자가 InvitationServiceImpl을 InternalUserService.getUserIfExist()로 리팩터링해 InvitationServiceImplTest 생성자 인자가 깨져 있던 것을 새 시그니처(userService mock)로 맞춤.
- InvitationImageServiceTest 8건, InvitationServiceImplTest 13건 통과.
