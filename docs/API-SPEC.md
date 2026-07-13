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

## 3. 청첩장 제작 (F-INV) — USER *(미구현 — 설계안)*

모든 엔드포인트: **인증 필요**, 그리고 **본인 소유 청첩장만** 접근 가능(타인 접근 시 `403/404`).

### 3.1 청첩장 생성(임시저장) — `POST /api/invitations`
근거: F-INV-01. 생성 시 `status = draft`.

- Request `{ "templateId": 1 }`  *(선택: 초기 sectionValues 포함 가능)*
- Response `201` — `data`
  ```json
  { "invitationId": 100, "templateId": 1, "status": "draft" }
  ```

### 3.2 내 청첩장 목록 — `GET /api/invitations`
- Response `200` — `data` — 본인 청첩장 배열(요약: id, templateId, status, slug, updatedAt)

### 3.3 청첩장 상세(편집용) — `GET /api/invitations/{invitationId}`
- 본인 것만. `sectionValues` 전체 포함.
- Response `200`

### 3.4 섹션 입력 저장 + 스키마 검증 — `PUT /api/invitations/{invitationId}`
근거: F-INV-02, ADR-002(write 시점 검증, 실패 시 틀린 필드 반환).

- Request
  ```json
  {
    "sectionValues": {
      "cover": { "groom_name": "…", "bride_name": "…", "date": "2026-10-10" },
      "gallery": { "photos": ["invitations/100/uuid1.jpg"] }
    }
  }
  ```
- 처리: `template.sectionSchema`로 `sectionValues` 검증 → 통과분만 jsonb 저장.
- Response `200`(저장된 값) / `400`(스키마 검증 실패, 틀린 필드 목록 — §0.4 형식 준용, code는 구현 시 확정)

### 3.5 사진 업로드 presigned URL 발급 — `POST /api/invitations/{invitationId}/images/presign`
근거: F-INV-03, ADR-001(로그인 사용자 한정, 서버는 바이너리 미중계).

- 인증: 필요(로그인 사용자만)
- Request `{ "fileName": "photo1.jpg", "contentType": "image/jpeg" }`
- 서버가 제약(허용 content-type, 최대 크기 등)을 적용해 발급.
- Response `200` — `data`
  ```json
  {
    "uploadUrl": "https://s3…(presigned)",
    "objectKey": "invitations/100/uuid1.jpg",
    "expiresIn": 300
  }
  ```
- 클라이언트는 `uploadUrl`로 S3에 직접 PUT 후, `objectKey`를 §3.4의 `sectionValues.photos`에 담아 저장.
- 후속: 업로드 후 검증·고아 객체 정리 정책(ADR-001).

### 3.6 청첩장 발행 — `POST /api/invitations/{invitationId}/publish`
근거: F-INV-04, ADR-003(무작위 slug, published만 공개).

- 처리: 최종 검증 통과 시 **추측 불가 무작위 slug** 발급, `status = published`, `published_at` 기록.
- Response `200` — `data`
  ```json
  { "invitationId": 100, "status": "published", "slug": "a1b2c3d4e5", "publicUrl": "/i/a1b2c3d4e5" }
  ```
- 에러: `400`(검증 실패로 발행 불가)

### 3.7 청첩장 수정/재발행 — (§3.4 재사용 + §3.6 재호출)
근거: F-INV-05 (P1). 발행된 청첩장을 수정 후 재발행. slug는 유지.

### 3.8 청첩장 삭제 — `DELETE /api/invitations/{invitationId}`
근거: F-INV-06 (P1). 연결 이미지 정리 정책 필요(ADR-001 후속).

- Response `204`

---

## 4. 하객 공개 열람 (F-VIEW) — Public *(미구현 — 설계안)*

### 4.1 슬러그로 공개 청첩장 조회 — `GET /api/public/invitations/{slug}`
근거: F-VIEW-01, ADR-003(무인증 공개 + published만 노출).

- 인증: **불필요**
- 조건: `status = published`인 청첩장만. draft/미존재 slug는 `404`.
- Response `200` — `data`
  ```json
  {
    "templateId": 1,
    "sectionValues": {
      "cover": { "groom_name": "…", "bride_name": "…", "date": "2026-10-10" },
      "gallery": { "photos": ["invitations/100/uuid1.jpg"] }
    }
  }
  ```
- 프론트(Next.js)가 `templateId`에 대응하는 React 컴포넌트로 `sectionValues`를 렌더링. 사진은 S3/CDN에서 로드.
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
| 청첩장 생성 | POST | `/api/invitations` | USER | ⬜ 미구현 | F-INV-01 |
| 내 청첩장 목록 | GET | `/api/invitations` | USER | ⬜ 미구현 | F-AUTH-03 |
| 청첩장 상세 | GET | `/api/invitations/{invitationId}` | USER | ⬜ 미구현 | F-AUTH-03 |
| 섹션 저장+검증 | PUT | `/api/invitations/{invitationId}` | USER | ⬜ 미구현 | F-INV-02 |
| presign 발급 | POST | `/api/invitations/{invitationId}/images/presign` | USER | ⬜ 미구현 | F-INV-03 |
| 발행 | POST | `/api/invitations/{invitationId}/publish` | USER | ⬜ 미구현 | F-INV-04 |
| 삭제 | DELETE | `/api/invitations/{invitationId}` | USER | ⬜ 미구현 | F-INV-06 |
| 공개 조회 | GET | `/api/public/invitations/{slug}` | - | ⬜ 미구현 | F-VIEW-01 |

> P2(방명록 F-EXT-01, RSVP F-EXT-02)는 범위 밖이라 미포함.

---

## 6. 미확정 / 후속 과제

| 주제 | 메모 | 관련 |
|---|---|---|
| 스키마 검증 실패 응답 code | 청첩장 저장(§3.4)용 code 정의 필요 | ADR-002 |
| 소셜 로그인 토큰 전달 방식 | 콜백 후 리다이렉트 vs 교환 API | ADR-003 |
| 다중 기기 세션 | refresh가 userId당 1개라 다기기 미지원 | ADR-004 |
| presign 제약 조건 | 허용 content-type·최대 크기 | ADR-001 |
| 삭제 시 이미지 정리 | 연결 S3 객체 정리 정책 | ADR-001 |
| 페이지네이션 | 목록 API 페이징 규약 | - |

## 7. 참고
- [ERD.md](./ERD.md) — 데이터 모델
- [SA-service-analysis.md](./SA-service-analysis.md) §4 — 기능 정의서(우선순위)
- [ADR-001](./adr/ADR-001-image-storage-s3-presigned.md) · [ADR-002](./adr/ADR-002-template-schema-jsonschema-jsonb.md) · [ADR-003](./adr/ADR-003-authentication-jwt-oauth2.md) · [ADR-004](./adr/ADR-004-refresh-token-redis.md)
