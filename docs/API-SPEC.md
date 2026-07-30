# MiniWed REST API 명세서

> 이 문서는 확정 문서([ARCHITECTURE.md](./ARCHITECTURE.md), [ADR-001/002/003/004](./adr/), [SA](./SA-service-analysis.md))와 기능 정의서(SA §4)를 근거로 한 REST API 명세입니다.
> **인증/계정(§1) 중 자체 회원가입·로그인·재발급·로그아웃은 구현되어 현재 코드를 반영합니다.** 나머지(템플릿·청첩장·공개조회 등)는 아직 컨트롤러가 없는 **설계안(초안)**으로, 구현 시 조정될 수 있습니다.
> 각 엔드포인트의 구현 여부는 §5 요약표의 **상태** 열을 참고하세요.
> 데이터 모델은 [ERD.md](./ERD.md)를 참고합니다.
>
> - 최초 작성일: 2026-07-01
> - 최종 갱신일: 2026-07-13 (auth 구현 반영)
> - 형식: REST / JSON, base path `/api`

---

## 0. 공통 규약

### 0.1 인증 (ADR-003)
- 인증은 **JWT access token** 기반. 보호 API는 헤더로 토큰을 제시한다.
  ```
  Authorization: Bearer {accessToken}
  ```
- 무상태 서버. access 만료 시 `POST /api/auth/v1/refresh`로 재발급.
- 권한: `USER`(자신의 청첩장 관리) / `ADMIN`(템플릿 관리).
- 서명: HS256(대칭키), access 30분 / refresh 14일. refresh는 Redis에 사용자당 1개 저장(ADR-004).

### 0.2 인증 경계 요약

| 구분 | 대상 | 인증 |
|---|---|---|
| 공개(Public) | 하객 청첩장 조회, 템플릿 목록/상세 | 불필요 |
| 사용자(USER) | 청첩장 생성·수정·발행·삭제, presign 발급 | 필요 |
| 관리자(ADMIN) | 템플릿 등록·수정 | 필요 + ADMIN |

### 0.3 공통 성공 응답 래퍼 (`ApiResponse`)
성공 응답은 모두 아래 래퍼로 감싼다(코드: `core/dto/ApiResponse`). `data`가 없으면(예: 204) 필드가 생략된다(`NON_NULL`).

```json
{
  "success": true,
  "status": 201,
  "data": { "…엔드포인트별 본문…": "…" }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `success` | boolean | 성공 응답은 항상 `true` |
| `status` | int | HTTP 상태 코드 값 |
| `data` | object/null | 엔드포인트별 본문. 없으면 생략 |

> 아래 각 엔드포인트의 Response 예시는 **`data` 내부 본문**만 표기한다. 실제 응답은 위 래퍼로 감싸진다.

### 0.4 공통 에러 응답 (`ErrorResponse`)
에러 응답은 성공 래퍼가 아니라 아래 포맷을 사용한다(코드: `core/exception/ErrorResponse`, `GlobalExceptionHandler`). `errors`는 요청 검증 실패 시에만 포함된다(`NON_NULL`).

```json
{
  "code": "VALIDATION_FAILED",
  "message": "입력값이 올바르지 않습니다.",
  "errors": [
    { "field": "password", "reason": "비밀번호는 8자 이상이어야 합니다." },
    { "field": "email", "reason": "이메일 형식이어야 합니다." }
  ]
}
```

현재 정의된 code(코드: `core/exception/ErrorCode`):

| HTTP | code | 상황 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | 요청 Bean Validation 실패 (틀린 필드 목록 `errors` 포함) |
| 401 | `UNAUTHENTICATED` | 자격 불일치(로그인) 또는 유효하지 않은/만료된 refresh 토큰 |
| 404 | `NOT_FOUND` | 사용자 등 리소스 없음 |
| 409 | `CONFLICT` | 이메일 중복 |

> 템플릿 스키마 검증 실패(청첩장 저장 §3.4)용 코드는 해당 기능 구현 시 추가된다(ADR-002).

---

## 1. 인증 / 계정 (F-AUTH)

> §1.1~1.5는 **구현됨**. §1.6(`/users/me`)와 소셜 로그인(§1.3)은 **미구현**(설계안).

### 1.1 자체 회원가입 — `POST /api/auth/v1/signup` *(구현됨)*
근거: F-AUTH-01, ADR-003(자체 이메일/비밀번호). 코드: `AuthController#signup`.

- 인증: 불필요
- Request (`UserCreateRequest`) — `password`는 8자 이상
  ```json
  { "email": "user@example.com", "password": "password123", "nickname": "지현" }
  ```
- Response `201` — `data` (`SignupResponse`)
  ```json
  { "userId": 1, "email": "user@example.com", "role": "USER" }
  ```
- 에러: `409 CONFLICT`(이메일 중복), `400 VALIDATION_FAILED`(형식/길이 위반)

### 1.2 자체 로그인 — `POST /api/auth/v1/login` *(구현됨)*
근거: F-AUTH-02, ADR-003. 코드: `AuthController#login`.

- 인증: 불필요
- Request (`LoginRequest`) `{ "email": "user@example.com", "password": "password123" }`
- Response `200` — `data` (`TokenResponse`)
  ```json
  { "accessToken": "…", "refreshToken": "…", "tokenType": "Bearer" }
  ```
- 처리: 발급한 refresh 토큰의 SHA-256 해시를 Redis에 사용자당 1개 저장(ADR-004).
- 에러: `401 UNAUTHENTICATED`(자격 불일치)

### 1.3 소셜 로그인 (OAuth2) — `GET /api/oauth2/authorize/{provider}` *(미구현)*
근거: ADR-003(소셜 OAuth2, kakao/google 등). Spring Security OAuth2 흐름.

- 인증: 불필요
- `{provider}`: `kakao` | `google` 등
- 브라우저를 provider 인증 페이지로 리다이렉트 → 콜백(`/login/oauth2/code/{provider}`) 처리 후 앱 토큰 발급.
- 신규면 계정 생성, 기존 이메일과 겹치면 account linking(정책은 ADR-003 후속).
- 최종적으로 클라이언트에 access/refresh 전달(리다이렉트 파라미터 또는 후속 교환 — 구현 시 확정).

### 1.4 토큰 재발급 — `POST /api/auth/v1/refresh` *(구현됨)*
근거: ADR-003(access+refresh 회전), ADR-004. 코드: `AuthController#refresh`.

- 인증: refresh token 제시(요청 본문)
- Request (`TokenReissueRequest`) `{ "refreshToken": "…" }`
- 처리: 저장된 해시와 대조 후 access(+회전된 refresh) 재발급.
- Response `200` — `data` (`TokenResponse`) — 새 access/refresh
- 에러: `401 UNAUTHENTICATED`(만료/폐기/불일치 refresh)

### 1.5 로그아웃 — `POST /api/auth/v1/logout` *(구현됨)*
근거: F-AUTH-02, ADR-003(무효화 보완), ADR-004. 코드: `AuthController#logout`.

- 인증: 필요 (access token). 서버는 `@AuthenticationPrincipal`의 userId로 처리.
- 처리: 해당 사용자의 refresh 토큰을 Redis에서 삭제.
- Response `204` (본문은 `{ "success": true, "status": 204 }`)

### 1.6 내 정보 — `GET /api/users/me` *(미구현)*
- 인증: 필요
- Response `200` — `data` `{ "userId": 1, "email": "...", "role": "USER", "nickname": "…" }`

---

## 2. 템플릿 (F-TPL) *(미구현 — 설계안)*

### 2.1 템플릿 목록 — `GET /api/templates`
근거: F-TPL-01.

- 인증: 불필요 (또는 로그인 사용자 — 정책상 공개 가능)
- Query: `category`(선택)
- Response `200` — `data`
  ```json
  {
    "items": [
      { "templateId": 1, "name": "미니멀 화이트", "thumbnail": "…", "category": "미니멀" }
    ]
  }
  ```

### 2.2 템플릿 상세 — `GET /api/templates/{templateId}`
근거: F-TPL-02. 사용 가능 섹션·변형·스키마 제공.

- 인증: 불필요
- Response `200` — `data`
  ```json
  {
    "templateId": 1,
    "name": "미니멀 화이트",
    "category": "미니멀",
    "sectionSchema": { "...": "JSON Schema 문서" },
    "variants": { "...": "스타일 변형 옵션" }
  }
  ```
- 프론트는 `sectionSchema`/`variants`로 입력 폼을 구성한다.

### 2.3 템플릿 등록 — `POST /api/admin/templates` *(ADMIN)*
근거: F-TPL-03, ADR-002(등록 스키마 메타 검증 필요).

- 인증: 필요 + `ADMIN`
- Request
  ```json
  {
    "name": "플로럴 로즈",
    "thumbnail": "templates/rose/thumb.jpg",
    "category": "플로럴",
    "sectionSchema": { "...": "유효한 JSON Schema" },
    "variants": { "...": "..." }
  }
  ```
- 서버는 등록되는 `sectionSchema` **자체의 유효성(메타 검증)**을 확인한다(ADR-002 후속).
- Response `201` — `data` `{ "templateId": 2 }`
- 에러: `400`(스키마 메타 검증 실패), `403`

### 2.4 템플릿 수정 — `PUT /api/admin/templates/{templateId}` *(ADMIN)*
근거: F-TPL-03.

- 인증: 필요 + `ADMIN`
- 주의: 스키마 변경 시 **기존 청첩장 데이터와 어긋날 수 있음**(버전 관리 미도입 — ADR-002).
- Response `200`

---

## 3. 청첩장 제작 (F-INV) — USER *(구현됨 — ADR-009)*

모든 엔드포인트: **인증 필요**, 그리고 **본인 소유 청첩장만** 접근 가능(타인 접근 시 `403 INVITATION_ACCESS_DENIED`, 미존재/UID 형식 오류 시 `404 INVITATION_NOT_FOUND`).

식별자는 외부 노출용 `invitationUid`(UUID)를 쓴다. 청첩장 값은 템플릿 섹션 인스턴스 식별자 `sectionId`로 키잉하며, 사용자가 고른 옵션은 `{sectionId → {optionKey → 값}}` 구조다(ADR-009). 검증은 저장 단계에 따라 다르다 — 임시저장은 구조·권한만, 발행은 컴포넌트 `dataSchema` 완결성까지(§3.6).

### 3.1 청첩장 생성(임시저장) — `POST /api/invitation/v1`
근거: F-INV-01, ADR-009. 생성 시 `status = DRAFT`.

- Request
  ```json
  {
    "templateUid": "0198e2c1-…",
    "sectionValues": { "cover": { "groomName": "철수" } },
    "selectedOptions": { "gallery-1": { "columns": 3 } }
  }
  ```
  `sectionValues`·`selectedOptions`는 선택(빈 청첩장으로 시작 가능).
- 처리: 템플릿 조회 → 임시저장 검증(모르는 sectionId·허용 안 된 옵션 거부) → 저장 → 값에 등장하는 이미지 연결.
- Response `201` — `data`(§3.3의 InvitationResponse 형태)

### 3.2 내 청첩장 목록 — `GET /api/invitation/v1`
- Response `200` — `data` — 본인 청첩장 배열(요약: `invitationUid`, `templateUid`, `status`, `slug`, `updatedAt`). 본문 jsonb는 목록에서 내려보내지 않는다.

### 3.3 청첩장 상세(편집용) — `GET /api/invitation/v1/{invitationUid}`
- 본인 것만. `sectionValues`의 이미지 키는 presigned GET URL로 치환되어 내려온다.
- Response `200` — `data`
  ```json
  {
    "invitationUid": "0198e2c1-…",
    "templateUid": "0198e2b0-…",
    "status": "DRAFT",
    "slug": null,
    "sectionValues": { "cover": { "groomName": "철수" },
                       "gallery-1": { "photos": ["https://s3…(presigned)"] } },
    "selectedOptions": { "gallery-1": { "columns": 3 } },
    "publishedAt": null,
    "updatedAt": "2026-07-21T10:00:00Z"
  }
  ```

### 3.4 섹션 입력 저장(임시저장) — `PUT /api/invitation/v1/{invitationUid}`
근거: F-INV-02, ADR-009. **전체 교체**다(부분 병합 아님 — "지운 것"과 "안 보낸 것"을 구분할 수 없기 때문).

- Request
  ```json
  {
    "sectionValues": {
      "cover": { "groomName": "철수", "brideName": "영희" },
      "gallery-1": { "photos": ["invitations/7/uuid1.jpg"] }
    },
    "selectedOptions": { "gallery-1": { "columns": 3 } }
  }
  ```
- 처리(DRAFT 기준): sectionId 화이트리스트 검사 → 옵션 검사 → 이미지 연결(본인 소유·PENDING만 LINKED, 빠진 키는 ORPHANED) → 저장. **발행 상태(PUBLISHED)면 발행 수준 완전 검증을 적용한다**(하객에게 노출된 청첩장이 깨지지 않도록).
- Response `200`(저장된 값, 이미지 키는 presigned URL 치환) / `400 UNKNOWN_SECTION_ID`·`INVALID_INVITATION_OPTIONS`·`INVALID_SECTION_VALUES` / `400 IMAGE_NOT_LINKABLE`(남의 이미지·다른 청첩장에 연결된 이미지)

### 3.5 사진 업로드 — `POST /api/invitation/v1/images` *(multipart/form-data)*
근거: F-INV-03, ADR-009(ADR-001 부분 개정 — 서버 경유 업로드).

- 인증: 필요(로그인 사용자만)
- Request: `multipart/form-data`, 파트명 `image` (허용 content-type `image/jpeg`·`image/png`, 최대 크기 서버 제한)
- 처리: 서버가 S3에 업로드(`invitations/{userId}/{uuid}.{ext}`) → `ImageUpload(status=PENDING)` 기록. DB엔 객체 키만 저장.
- Response `201` — `data`
  ```json
  { "objectKey": "invitations/7/uuid1.jpg", "url": "https://s3…(presigned, 미리보기용)" }
  ```
- 클라이언트는 `objectKey`를 §3.4의 `sectionValues` 이미지 필드에 담아 저장 요청한다. 저장 시점에 그 청첩장으로 LINKED 확정된다.
- 에러: `400 INVALID_IMAGE_TYPE`·`IMAGE_TOO_LARGE`

### 3.6 청첩장 발행 — `POST /api/invitation/v1/{invitationUid}/publish`
근거: F-INV-04, ADR-003·ADR-009(무작위 slug, PUBLISHED만 공개).

- 처리: 템플릿의 **모든 섹션**을 컴포넌트 `dataSchema`로 완전 검증(값이 없는 섹션도 빈 값으로 태워 `required` 위반으로 걸린다) → 통과 시 **추측 불가 무작위 slug**(22자 URL-safe) 발급, `status = PUBLISHED`, `publishedAt` 기록.
- 재발행이면 기존 slug를 그대로 쓴다(공유된 링크가 살아 있어야 한다).
- Response `200` — `data`(§3.3 형태, `status = PUBLISHED`, `slug` 채워짐)
- 에러: `400`(스키마 완결성 검증 실패로 발행 불가) / `409 INVITATION_ALREADY_PUBLISHED`

### 3.7 발행 취소 — `POST /api/invitation/v1/{invitationUid}/unpublish`
근거: ADR-009. `status = DRAFT`로 되돌리고 `publishedAt`을 비운다. **slug는 회수하지 않는다**(이미 공유된 링크를 다른 청첩장이 넘겨받지 않도록). 재발행 시 같은 slug로 되살아난다.

- Response `200` — `data`(§3.3 형태, `status = DRAFT`)
- 에러: `409 INVITATION_NOT_PUBLISHED`(발행되지 않은 청첩장)

### 3.8 청첩장 삭제 — `DELETE /api/invitation/v1/{invitationUid}`
근거: F-INV-06, ADR-009. 삭제 전 연결된 이미지를 모두 끊어 `ORPHANED`로 만든다(FK 제약 때문). 실제 S3 삭제는 후속 배치.

- Response `200` — `data: null`(No Content 의미)

---

## 4. 하객 공개 열람 (F-VIEW) — Public *(구현됨 — ADR-009)*

### 4.1 슬러그로 공개 청첩장 조회 — `GET /api/public/invitation/v1/{slug}`
근거: F-VIEW-01, ADR-003·ADR-009(무인증 공개 + PUBLISHED만 노출).

- 인증: **불필요**
- 조건: `status = PUBLISHED`인 청첩장만. DRAFT/미존재 slug는 모두 동일하게 `404 INVITATION_NOT_FOUND`(DRAFT의 존재 자체를 숨긴다).
- 응답은 렌더러가 한 번의 호출로 필요한 것을 받도록 **템플릿 레시피 + 청첩장 값을 합친 페이로드**다. `sections`에는 사용자가 고른 옵션(`selectedOptions`)이 템플릿 고정옵션 위에 덮여 있고, `sectionValues`의 이미지 키는 presigned GET URL로 치환되어 있다.
- Response `200` — `data`
  ```json
  {
    "invitationUid": "0198e2c1-…",
    "sections": [
      { "sectionId": "cover", "componentUId": "CoverBasic", "options": { "align": "center" } },
      { "sectionId": "gallery-1", "componentUId": "GalleryGrid", "options": { "columns": 3 } }
    ],
    "theme": { "fontFamily": "serif" },
    "sectionValues": {
      "cover": { "groomName": "철수", "brideName": "영희" },
      "gallery-1": { "photos": ["https://s3…(presigned)"] }
    },
    "publishedAt": "2026-07-21T10:00:00Z"
  }
  ```
- 프론트(Next.js)가 각 섹션의 `componentUId`에 대응하는 React 컴포넌트로 `sectionValues`를 렌더링. 사진은 presigned URL로 로드.
- 응답에 검색엔진 비노출(noindex) 헤더 적용 권장(ADR-003).
- **주의**: slug를 아는 사람은 계좌번호 등 민감정보 열람 가능(청첩장 특성상 감수 — ADR-003).

---

## 5. 엔드포인트 요약표

| 기능 | Method | Path | 인증 | 상태 | 근거 |
|---|---|---|---|---|---|
| 회원가입 | POST | `/api/auth/v1/signup` | - | ✅ 구현 | F-AUTH-01 |
| 로그인 | POST | `/api/auth/v1/login` | - | ✅ 구현 | F-AUTH-02 |
| 토큰 재발급 | POST | `/api/auth/v1/refresh` | refresh | ✅ 구현 | ADR-003 |
| 로그아웃 | POST | `/api/auth/v1/logout` | USER | ✅ 구현 | F-AUTH-02 |
| 소셜 로그인 | GET | `/api/oauth2/authorize/{provider}` | - | ⬜ 미구현 | ADR-003 |
| 내 정보 | GET | `/api/users/me` | USER | ⬜ 미구현 | - |
| 템플릿 목록 | GET | `/api/templates` | - | ⬜ 미구현 | F-TPL-01 |
| 템플릿 상세 | GET | `/api/templates/{templateId}` | - | ⬜ 미구현 | F-TPL-02 |
| 템플릿 등록 | POST | `/api/admin/templates` | ADMIN | ⬜ 미구현 | F-TPL-03 |
| 템플릿 수정 | PUT | `/api/admin/templates/{templateId}` | ADMIN | ⬜ 미구현 | F-TPL-03 |
| 청첩장 생성 | POST | `/api/invitation/v1` | USER | ✅ 구현 | F-INV-01 |
| 내 청첩장 목록 | GET | `/api/invitation/v1` | USER | ✅ 구현 | F-AUTH-03 |
| 청첩장 상세 | GET | `/api/invitation/v1/{invitationUid}` | USER | ✅ 구현 | F-AUTH-03 |
| 섹션 저장 | PUT | `/api/invitation/v1/{invitationUid}` | USER | ✅ 구현 | F-INV-02 |
| 사진 업로드 | POST | `/api/invitation/v1/images` | USER | ✅ 구현 | F-INV-03 |
| 발행 | POST | `/api/invitation/v1/{invitationUid}/publish` | USER | ✅ 구현 | F-INV-04 |
| 발행 취소 | POST | `/api/invitation/v1/{invitationUid}/unpublish` | USER | ✅ 구현 | ADR-009 |
| 삭제 | DELETE | `/api/invitation/v1/{invitationUid}` | USER | ✅ 구현 | F-INV-06 |
| 공개 조회 | GET | `/api/public/invitation/v1/{slug}` | - | ✅ 구현 | F-VIEW-01 |

> P2(방명록 F-EXT-01, RSVP F-EXT-02)는 범위 밖이라 미포함.

---

## 6. 미확정 / 후속 과제

| 주제 | 메모 | 관련 |
|---|---|---|
| HTTP 상태코드 정합 | 생성/삭제가 바디엔 201/204인데 실제 응답은 200(`ResponseEntity.ok` 래핑) — 컨트롤러 전반 정리 필요 | - |
| 이미지 다중 청첩장 | 같은 키를 여러 청첩장에 쓸 때 정책(현재는 재연결 거부) | ADR-009 |
| ORPHANED 이미지 삭제 | 연결 끊긴 S3 객체 실삭제 배치 | ADR-001·ADR-009 |
| S3 키의 내부 PK 노출 | 공개 presigned URL 경로에 소유자 userId 노출 — 키 포맷 변경 검토 | ADR-009 |
| 소셜 로그인 토큰 전달 방식 | 콜백 후 리다이렉트 vs 교환 API | ADR-003 |
| 다중 기기 세션 | refresh가 userId당 1개라 다기기 미지원 | ADR-004 |
| 페이지네이션 | 목록 API 페이징 규약 | - |

## 7. 참고
- [ERD.md](./ERD.md) — 데이터 모델
- [SA-service-analysis.md](./SA-service-analysis.md) §4 — 기능 정의서(우선순위)
- [ADR-001](./adr/ADR-001-image-storage-s3-presigned.md) · [ADR-002](./adr/ADR-002-template-schema-jsonschema-jsonb.md) · [ADR-003](./adr/ADR-003-authentication-jwt-oauth2.md) · [ADR-004](./adr/ADR-004-refresh-token-redis.md) · [ADR-007](./adr/ADR-007-section-component-template-model.md) · [ADR-009](./adr/ADR-009-invitation-persistence-model.md)
