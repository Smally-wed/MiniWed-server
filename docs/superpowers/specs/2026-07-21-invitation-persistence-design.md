# 청첩장 저장 설계 (Invitation Persistence)

- 작성일: 2026-07-21
- 근거 ADR: [ADR-009](../../adr/ADR-009-invitation-persistence-model.md)
- 관련 ADR: ADR-001(부분 개정), ADR-002, ADR-007, ADR-008

## 1. 범위

**포함**: `Invitation` 엔티티 재설계, 생성·조회·저장·발행·삭제 API, 청첩장 사진 업로드 API,
컴포넌트 스키마 기반 검증, 이미지 연결/고아 처리, 하객용 공개 조회 API.

**제외(별도 스펙)**: 참석여부(RSVP)·방명록 — 하객이 쓰는 데이터라 테이블·수명주기·인증 경계가 다르다.
조회수 통계, CDN 연동, `ORPHANED` 이미지 실삭제 배치.

## 2. 데이터 모델

### 2.1 `Invitation` 변경

| 필드 | 상태 | 설명 |
|---|---|---|
| `id` | 유지 | PK, 외부 미노출 |
| `invitationUid` (UUID) | **신설** | 외부 식별자. `@PrePersist`에서 `UuidCreator.getTimeOrderedEpoch()` — `Template` 패턴 동일 |
| `user`, `template` | 유지 | `@ManyToOne(LAZY)` |
| `slug` | 유지 | 발행 시 발급, 그 전 null |
| `status` | 유지 | `DRAFT` / `PUBLISHED` |
| `sectionValues` (jsonb) | **의미 변경** | `{sectionId → {필드값}}` |
| `selectedOptions` (jsonb) | **신설** | `{optionKey → 값}` |
| `publishedAt` | 유지 | |

도메인 메서드(세터 대신):

```java
void updateContent(Map<String,Object> sectionValues, Map<String,Object> selectedOptions);
void publish(String slug);   // status=PUBLISHED, publishedAt=now, slug 설정
void unpublish();            // status=DRAFT, publishedAt=null, slug는 유지(재사용 안 함)
boolean isOwnedBy(Long userId);
```

`unpublish()`에서 slug를 유지하는 이유: 이미 공유된 링크를 다른 청첩장이 넘겨받는 사고를 막는다.
재발행 시 같은 slug로 되살아난다.

### 2.2 `Template.sections`에 `sectionId` 필수화

각 원소 형태: `{ "sectionId": "...", "componentUId": "...", "options": {...}, "editable": [...] }`

`TemplateServiceImpl.validateRecipe()`에 추가할 검사:
- `sectionId`가 비어 있지 않은 문자열인가 → `INVALID_TEMPLATE_RECIPE`
- 한 템플릿 안에서 `sectionId`가 중복되지 않는가 → `INVALID_TEMPLATE_RECIPE`

`editable`은 해당 섹션에서 사용자가 바꿀 수 있는 옵션 키 목록이다. 없으면 전부 고정으로 본다.

### 2.3 `ImageUpload` 변경

| 필드 | 상태 | 설명 |
|---|---|---|
| `uploader` (User FK) | **신설** | `nullable = false`. 남의 업로드 키를 붙이는 것을 차단 |
| `status` | **값 추가** | `PENDING` / `LINKED` / `ORPHANED` |

`ImageUploadRepository`에 추가:
```java
Optional<ImageUpload> findByObjectKey(String objectKey);
List<ImageUpload> findAllByObjectKeyIn(Collection<String> objectKeys);
List<ImageUpload> findAllByInvitation(Invitation invitation);
```

### 2.4 `InvitationRepository`에 추가

```java
Optional<Invitation> findByInvitationUid(UUID invitationUid);
Optional<Invitation> findBySlugAndStatus(String slug, InvitationStatus status);
List<Invitation> findAllByUserIdOrderByUpdatedAtDesc(Long userId);
boolean existsBySlug(String slug);
```

## 3. 저장되는 데이터 예시

템플릿 레시피:
```json
{
  "sections": [
    { "sectionId": "cover",     "componentUId": "CoverBasic",  "options": {"align": "center"} },
    { "sectionId": "gallery-1", "componentUId": "GalleryGrid", "editable": ["columns"] },
    { "sectionId": "gallery-2", "componentUId": "GalleryGrid", "editable": ["columns"] },
    { "sectionId": "account",   "componentUId": "AccountList" }
  ],
  "theme": { "fontFamily": "serif" }
}
```

청첩장 값:
```json
{
  "sectionValues": {
    "cover":     { "groomName": "…", "brideName": "…", "weddingAt": "2026-10-10T11:00:00" },
    "gallery-1": { "photos": ["invitations/7/9f2c….jpg", "invitations/7/a13b….jpg"] },
    "gallery-2": { "photos": ["invitations/7/c77e….jpg"] },
    "account":   { "groom": {"bank": "…", "number": "…"}, "bride": {"bank": "…", "number": "…"} }
  },
  "selectedOptions": { "columns": 3 }
}
```

계좌번호·오시는길·연락처는 전부 `Component.dataSchema`가 정의하므로 서버 스키마 변경 없이 표현된다.

## 4. API 명세

기존 컨트롤러 관례(`/api/{도메인}/v1`, `ApiResponse.of(...)` 래핑)를 따른다.

| # | 메서드 | 경로 | 인증 | 설명 |
|---|---|---|---|---|
| 1 | POST | `/api/invitation/v1` | 소유자 | 생성 (DRAFT) |
| 2 | GET | `/api/invitation/v1/{invitationUid}` | 소유자 | 편집용 조회 |
| 3 | GET | `/api/invitation/v1` | 소유자 | 내 청첩장 목록 |
| 4 | PUT | `/api/invitation/v1/{invitationUid}` | 소유자 | 저장/임시저장 (전체 교체) |
| 5 | POST | `/api/invitation/v1/{invitationUid}/publish` | 소유자 | 발행 |
| 6 | POST | `/api/invitation/v1/{invitationUid}/unpublish` | 소유자 | 발행 취소 |
| 7 | DELETE | `/api/invitation/v1/{invitationUid}` | 소유자 | 삭제 |
| 8 | POST | `/api/invitation/v1/images` | 로그인 | 사진 업로드 (multipart) |
| 9 | GET | `/api/public/invitation/v1/{slug}` | **무인증** | 하객 열람 |

### 4.1 DTO

```java
// 생성
public record InvitationCreateRequest(
        @NotBlank String templateUid,
        Map<String, Object> sectionValues,     // null 허용(빈 청첩장으로 시작)
        Map<String, Object> selectedOptions
) {}

// 저장 (전체 교체)
public record InvitationUpdateRequest(
        @NotNull Map<String, Object> sectionValues,
        Map<String, Object> selectedOptions
) {}

// 편집용 응답
public record InvitationResponse(
        String invitationUid,
        String templateUid,
        InvitationStatus status,
        String slug,
        Map<String, Object> sectionValues,     // 이미지 키 → presigned URL 치환됨
        Map<String, Object> selectedOptions,
        Instant publishedAt,
        Instant updatedAt
) {}

// 목록용 (본문 제외 — 목록에서 jsonb를 다 내려보내지 않는다)
public record InvitationSummaryResponse(
        String invitationUid, String templateUid,
        InvitationStatus status, String slug, Instant updatedAt
) {}

// 업로드 응답
public record ImageUploadResponse(String objectKey, String url) {}

// 하객 공개 조회 — 렌더에 필요한 것을 한 번에
public record PublicInvitationResponse(
        String invitationUid,
        List<Map<String, Object>> sections,    // 템플릿 레시피
        Map<String, Object> theme,             // 템플릿 테마 + selectedOptions 덮어쓴 결과
        Map<String, Object> sectionValues,     // 이미지 키 → presigned URL 치환됨
        Instant publishedAt
) {}
```

### 4.2 저장(PUT)이 전체 교체인 이유

부분 병합(PATCH)이면 "값을 지운 것"과 "안 보낸 것"을 구분할 수 없다. 편집기는 항상 전체 상태를
들고 있으므로 전체 교체가 클라이언트에게도 더 단순하다. 대신 **덮어쓰기 사고를 막기 위해
`updatedAt` 기반 낙관적 잠금은 이번 범위에서 다루지 않는다**(1인 편집 가정, 후속 과제).

### 4.3 slug 생성

`java.security.SecureRandom` 기반 22자 URL-safe Base64(128비트). 충돌 시
`existsBySlug()`로 확인해 최대 5회 재시도, 그래도 실패하면 `INTERNAL_SERVER_ERROR`.
발행 취소 후 재발행 시에는 기존 slug를 그대로 쓴다(§2.1).

## 5. 검증 파이프라인

### 5.1 저장(PUT) — DRAFT 기준

```
1. 소유자 확인            invitation.isOwnedBy(currentUserId)  → 아니면 403
2. 템플릿 조회            templateService.getTemplate(templateUid)  (Redis 캐시 경유)
3. sectionId 화이트리스트  sectionValues.keySet() ⊆ 템플릿 sectionId 집합 → 아니면 400
4. 옵션 검증              selectedOptions의 각 키가
                          (a) 어느 섹션의 editable 목록에 있고
                          (b) OptionDefinition.allowedValues에 값이 포함되는가
5. 이미지 연결            §6
6. 저장                   invitation.updateContent(...)
```

3번을 화이트리스트로 두는 이유: 템플릿에 없는 `sectionId`를 허용하면 검증되지 않은 임의 JSON이
jsonb에 쌓이고, 나중에 그 템플릿이 해당 섹션을 갖게 되면 검증을 통과한 적 없는 값이 살아난다.

### 5.2 발행(publish) — 완결성 검증 추가

5.1의 1~5를 모두 수행한 뒤:

```
6. 전 섹션 완전 검증  템플릿의 모든 sectionId에 대해
                     internalComponentService.validateComponentJsontData(
                         componentUId, sectionValues.get(sectionId), 섹션 옵션값)
                     → required 미충족 포함 위반 시 400
7. slug 발급 + publish()
```

즉 **DRAFT에 없는 섹션도 발행 시에는 반드시 있어야 한다.** 값이 아예 없는 섹션은 빈 맵으로
검증에 태워 `required` 위반으로 걸리게 한다.

### 5.3 재사용할 기존 코드와 수정 사항

`InternalComponentService.validateComponentJsontData(componentUid, data, optionData)`가 이미
컴포넌트 단위 검증을 수행하므로 그대로 쓴다. 다만 **두 가지를 고쳐야 한다.**

1. `optionSchema`가 null인 컴포넌트에서 `validateData(null, ...)`로 NPE가 난다. null이면
   옵션 검증을 건너뛰도록 가드를 추가한다.
2. `data`가 null일 때도 빈 맵으로 다루도록 한다(§5.2의 "없는 섹션" 처리).

이는 이번 작업에 직접 필요한 수정이며, 그 외 컴포넌트 도메인은 건드리지 않는다.

## 6. 이미지 처리

### 6.1 업로드 (`POST /api/invitation/v1/images`)

```
MultipartFile → storageService.upload(file, "invitations/{userId}/")
             → ImageUpload(uploader=현재 사용자, objectKey, status=PENDING) 저장
             → { objectKey, url(presigned GET) } 반환
```

`StorageService`는 이미 content-type 화이트리스트(jpeg/png)와 크기 제한을 검증하므로
추가 검증 로직은 두지 않는다. 응답에 presigned URL을 같이 주는 이유는 클라이언트가
업로드 직후 미리보기를 띄우기 위해서다. **DB·캐시에는 objectKey만 저장한다**(ADR-008).

### 6.2 연결 (저장 시)

```java
// sectionValues 전체를 재귀 순회해 "invitations/"로 시작하는 문자열을 수집
Set<String> collectImageKeys(Object node)   // Map, List, String을 재귀 처리
```

수집한 키에 대해:

| 상황 | 처리 |
|---|---|
| 업로드 기록 없음 | 무시(사용자가 텍스트에 우연히 넣은 문자열일 수 있음) |
| 업로더가 현재 사용자가 아님 | 400 `IMAGE_NOT_LINKABLE` |
| `PENDING` | `LINKED` + `invitation` 연결 |
| 이미 이 청첩장에 `LINKED` | 그대로 둠 |
| 직전 저장엔 있었으나 이번엔 없음 | `ORPHANED` (실제 S3 삭제는 후속 배치) |

"업로드 기록 없음"을 무시하는 이유: 프리픽스 스캔은 정밀하지 않아 오탐이 가능한데, 오탐 때문에
사용자의 저장이 실패하면 안 된다. 반면 **남의 키를 붙이는 것은 명확한 권한 위반**이므로 거부한다.

### 6.3 조회 시 URL 치환

`sectionValues`를 재귀 순회하며 `invitations/` 프리픽스 문자열을 presigned GET URL로 바꾼 사본을
만들어 응답한다. 저장된 값은 건드리지 않는다. 수집과 치환은 같은 순회 로직을 공유하므로
`SectionValueImageScanner`(core 또는 image 도메인) 하나로 구현한다.

## 7. 에러코드 · 인증

### 7.1 `ErrorCode` 추가

```java
UNAUTHENTICATED_REQUIRED(UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다."),
INVITATION_NOT_FOUND(NOT_FOUND, "NOT_FOUND", "청첩장을 찾을 수 없습니다."),
INVITATION_ACCESS_DENIED(FORBIDDEN, "FORBIDDEN", "해당 청첩장에 대한 권한이 없습니다."),
INVITATION_ALREADY_PUBLISHED(CONFLICT, "CONFLICT", "이미 발행된 청첩장입니다."),
UNKNOWN_SECTION_ID(BAD_REQUEST, "BAD_REQUEST", "템플릿에 없는 섹션입니다."),
INVALID_INVITATION_OPTIONS(BAD_REQUEST, "BAD_REQUEST", "수정할 수 없거나 허용되지 않은 옵션 값입니다."),
IMAGE_NOT_LINKABLE(BAD_REQUEST, "BAD_REQUEST", "연결할 수 없는 이미지입니다."),
SLUG_GENERATION_FAILED(INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "공개 주소 생성에 실패했습니다."),
```

`INVALID_SECTION_VALUES`는 이미 존재하므로 재사용한다.
신규 예외 클래스 `InvitationException extends BusinessException`을 추가한다(기존 도메인 관례).

### 7.2 현재 사용자 식별

`JwtAuthenticationFilter`가 principal에 **userId 문자열**을 넣는다. 컨트롤러에서
`@AuthenticationPrincipal String userId`로 받고, null이면 `UNAUTHENTICATED_REQUIRED`.
매 컨트롤러에서 반복되지 않게 `core/security/CurrentUser` 헬퍼(정적 메서드 또는 컴포넌트)로 추출한다.

### 7.3 `SecurityConfig` 인가 규칙 좁히기

현재 `anyRequest().permitAll()`이다. 이번 작업 범위에 해당하는 경로만 좁힌다.

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/public/**").permitAll()
        .requestMatchers("/api/invitation/**").authenticated()
        .anyRequest().permitAll())
```

다른 도메인의 인가 규칙은 이번 요청 범위 밖이므로 그대로 둔다.

## 8. 클래스 구조

```
domain/invitation/
├── controller/  InvitationController, PublicInvitationController
├── dto/         InvitationCreateRequest, InvitationUpdateRequest,
│                InvitationResponse, InvitationSummaryResponse, PublicInvitationResponse
├── entity/      Invitation (수정)
├── enums/       InvitationStatus (유지)
├── repository/  InvitationRepository (쿼리 추가)
└── service/
    ├── InvitationService          (인터페이스: 생성·조회·저장·발행·삭제)
    ├── InvitationServiceImpl
    ├── PublicInvitationService    (하객 공개 조회 — 인증 경계가 달라 분리)
    ├── PublicInvitationServiceImpl
    ├── InvitationValidator        (§5 검증 파이프라인)
    └── SlugGenerator

domain/image/
├── controller/  InvitationImageController
├── service/     InvitationImageService  (업로드 기록·연결·고아 처리)
└── util/        SectionValueImageScanner  (§6.2, §6.3 공용 순회)
```

검증을 `InvitationValidator`로 분리하는 이유: 저장 경로와 발행 경로가 검증 강도만 다르고 나머지는
같아서, 서비스에 섞으면 두 메서드에 같은 코드가 흩어진다. 단위 테스트도 서비스 없이 가능해진다.

**캐시**: 청첩장은 편집이 잦고 사용자별 데이터라 Redis 캐싱을 하지 않는다. 템플릿 조회는
기존 캐시를 그대로 탄다.

**트랜잭션**: S3 업로드(네트워크)는 트랜잭션 밖에서 수행한다 —
`TemplateServiceImpl.createTemplate()`의 기존 방침과 동일.

## 9. 테스트 계획

| 대상 | 종류 | 검증 내용 |
|---|---|---|
| `SectionValueImageScanner` | 단위 | 중첩 Map/List에서 키 수집, 프리픽스 무관 문자열 무시, URL 치환이 원본 불변 |
| `InvitationValidator` | 단위 | 미등록 sectionId 거부, editable 아닌 옵션 거부, 허용값 밖 옵션 거부, DRAFT는 required 미충족 통과, publish는 거부 |
| `SlugGenerator` | 단위 | 길이·문자셋, 충돌 시 재시도 |
| `Invitation` | 단위 | `publish()` 상태 전이, `unpublish()` 후 slug 유지, `isOwnedBy` |
| `InvitationServiceImpl` | 통합 | 생성 → 임시저장 → 발행 전체 흐름, 남의 청첩장 접근 403 |
| `InvitationImageService` | 통합 | PENDING → LINKED, 남의 키 거부, 값에서 빠진 키 ORPHANED |
| `PublicInvitationService` | 통합 | PUBLISHED만 조회, DRAFT는 404, 응답에 presigned URL |
| `TemplateServiceImpl` | 통합 | sectionId 없음/중복 시 `INVALID_TEMPLATE_RECIPE` |

기존 테스트 관례(`src/test/java/smally/server/**`)를 따르고, 통합 테스트는 Postgres·Redis가 필요하다.

## 10. 미해결 · 후속

- 동시 편집 시 덮어쓰기 방지(낙관적 잠금) — 1인 편집 가정으로 이번엔 제외
- `ORPHANED` 이미지 실제 S3 삭제 배치 (ADR-001 후속과 통합)
- RSVP·방명록 테이블 (별도 스펙)
- `docs/ERD.md` 갱신
- 청첩장 목록 페이징 — 사용자당 건수가 적어 이번엔 전체 반환
