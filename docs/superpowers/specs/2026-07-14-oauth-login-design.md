# OAuth 소셜 로그인/회원가입 설계

- 작성일: 2026-07-14
- 관련 ADR: [ADR-003](../../adr/ADR-003-authentication-jwt-oauth2.md) (JWT + 소셜/자체 병행 승인)
- 상태: 설계 승인됨, 구현 예정

## 배경

현재 인증은 자체 이메일/비밀번호 방식만 구현되어 있다 (`AuthService`: signup / login / reissue / logout).
ADR-003에서 이미 "소셜 로그인(OAuth2) + 자체 계정 병행"이 승인되었고, 데이터 모델(`OauthAccount`, `User.password` nullable)도 준비되어 있다.
이 문서는 그 후속 과제였던 **OAuth provider 연동**과 **account linking 정책**을 구체화한다.

## 결정 요약

| 항목 | 결정 |
|------|------|
| OAuth 흐름 | **토큰 릴레이** — 프론트가 provider access token을 받아 서버로 전달, 서버가 검증 후 자체 JWT 발급 |
| 지원 provider | 카카오, 구글, 네이버 |
| account linking | **이메일로 자동 링킹** — 소셜 이메일이 기존 User 이메일과 같으면 그 User에 OauthAccount 연결 |
| 이메일 미제공 시 | **가입 거부** — provider가 이메일을 주지 않으면 로그인 거부 (provider 콘솔에서 이메일 필수 동의 설정) |
| 스키마 변경 | 없음 (User.email은 not-null + unique 유지) |
| 신규 의존성 | 없음 (Spring `RestClient` + Jackson 기존 보유, oauth2-client 스타터 불필요) |

## API

**엔드포인트:** `POST /api/auth/v1/oauth/{provider}` — `provider` ∈ `kakao | google | naver`

**요청 본문:**
```json
{ "accessToken": "<provider가 프론트에 발급한 access token>" }
```

**응답:** 기존 자체 로그인과 동일한 `TokenResponse` (우리 access + refresh token)

프론트가 각 provider SDK로 로그인을 완료해 **provider access token**을 얻은 뒤, 그 토큰만 서버로 전달한다. (authorization code가 아니라 access token 릴레이 — Kakao/Naver JS SDK 표준 흐름과 일치)

## 서버 처리 흐름 (하나의 트랜잭션)

1. `provider`별 클라이언트로 provider userinfo API 호출 → `providerUserId`, `email`, `nickname` 정규화 (`OauthUserInfo`)
2. `email`이 없거나 비어 있으면 → `AuthException` (이메일 미동의 → 가입 거부)
3. `OauthAccount(provider, providerUserId)` 조회
   - **있으면** → 연결된 `User`로 곧장 토큰 발급 (재로그인)
   - **없으면** → `email`로 `User` 조회
     - `User` **있으면** → 자동 링킹: 그 User에 `OauthAccount` 새로 연결
     - `User` **없으면** → 새 `User`(email, password=null, role=USER, nickname) 생성 후 `OauthAccount` 연결
4. 기존 `AuthService.issueTokens(user)` 재사용해 우리 JWT(access + refresh) 발급, refresh는 기존과 동일하게 Redis에 해시 저장

## 컴포넌트 및 패키지 배치

도메인 개념/포트는 `domain/auth`에 두고, 외부 provider HTTP 어댑터는 그 하위 패키지로 분리한다.
`core`에는 두지 않는다 — `core`는 `ApiResponse`·예외 프레임워크처럼 도메인 무관한 범용 플러밍이고, OAuth provider 연동은 "사용자 인증"이라는 특정 유스케이스에 종속된 외부 통합이기 때문이다. (별도 `infra` 계층 신설은 S3 연동 구현 시점에 재검토)

```
domain/auth/oauth/          OauthProvider, OauthUserInfo, OauthClient(포트), OauthClientResolver
domain/auth/oauth/client/   KakaoOauthClient, GoogleOauthClient, NaverOauthClient (어댑터)
domain/auth/dto/            OauthLoginRequest
```

- **`OauthProvider`** (`domain/auth/oauth`, enum: KAKAO, GOOGLE, NAVER) — path 값 파싱/검증. 미지원 값이면 에러.
- **`OauthUserInfo`** (`domain/auth/oauth`, record: `provider`, `providerUserId`, `email`, `nickname`) — provider 응답 정규화 결과.
- **`OauthClient`** (`domain/auth/oauth`, 인터페이스 = 포트) + 어댑터 `KakaoOauthClient` / `GoogleOauthClient` / `NaverOauthClient` (`domain/auth/oauth/client`) — 각자 provider userinfo 호출 후 `OauthUserInfo`로 매핑. Spring `RestClient` 사용. provider별 userinfo 엔드포인트 URL은 상수 또는 설정값. 토큰 릴레이라 client secret은 서버에 불필요.
- **`OauthClientResolver`** (`domain/auth/oauth`) — `OauthProvider` enum으로 알맞은 `OauthClient` 선택 (Map<OauthProvider, OauthClient> 주입).
- **`OauthLoginRequest`** (`domain/auth/dto`, record: `accessToken`) — 요청 DTO.
- **`AuthController`** — `POST /api/auth/v1/oauth/{provider}` 엔드포인트 1개 추가.
- **`AuthService`** — `oauthLogin(OauthProvider provider, String accessToken)` 메서드 추가. 기존 `issueTokens(User)` 재사용.

### 각 컴포넌트의 책임/경계

- `OauthClient` 구현체: "provider access token → 정규화된 사용자 정보". 외부 HTTP 호출만 담당, 우리 도메인 로직 없음.
- `OauthClientResolver`: provider 선택만 담당.
- `AuthService.oauthLogin`: 조회/링킹/가입/토큰발급 오케스트레이션. provider별 세부사항은 모른다 (`OauthClient` 뒤에 숨음).

## 변경 범위

- **신규 파일**: `domain/auth/oauth/`(`OauthProvider`, `OauthUserInfo`, `OauthClient`, `OauthClientResolver`), `domain/auth/oauth/client/`(`KakaoOauthClient`, `GoogleOauthClient`, `NaverOauthClient`), `domain/auth/dto/OauthLoginRequest`
- **수정**: `AuthController`(+1 엔드포인트), `AuthService`(+1 메서드), `ErrorCode`(+에러 코드)
- **스키마 변경 없음** (`OauthAccount`, `User` 그대로)
- **build.gradle 변경 없음**

## 에러 코드 (신규)

`ErrorCode`에 추가:
- 지원하지 않는 OAuth provider
- provider 통신 실패 (userinfo 호출 오류/토큰 무효)
- 이메일 미동의 (가입 거부)

## 테스트

- **`AuthService.oauthLogin` 단위 테스트** — `OauthClient` 모킹으로 4개 분기 검증:
  1. 신규 소셜 가입 (User·OauthAccount 생성)
  2. 기존 소셜 재로그인 (OauthAccount 존재)
  3. 이메일 자동 링킹 (기존 User + 새 OauthAccount)
  4. 이메일 미동의 → 거부
- **provider 클라이언트 테스트** — `MockRestServiceServer`로 userinfo 응답 파싱/정규화 검증.

## 미해결/후속

- provider별 userinfo 엔드포인트 URL·요청 형식 확정 (구현 시 각 provider 문서 확인).
- provider 콘솔에서 이메일을 **필수 동의** 항목으로 설정 (카카오/네이버). 운영 준비 시 작업.
- 필요 시 ADR-003의 "후속 조치" 항목에 본 결정(토큰 릴레이 / 이메일 자동링킹 / 이메일 미동의 거부) 반영.
