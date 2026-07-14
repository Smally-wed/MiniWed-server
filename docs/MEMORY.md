# MEMORY

프로젝트의 중요한 결정을 시간순으로 남긴다. 상세 배경·대안은 `docs/adr/` 참고.

## 2026-06-30 — 초기 아키텍처 방향 확정

전체 개요는 [docs/ARCHITECTURE.md](./ARCHITECTURE.md) 참고.

- **데이터/렌더러 분리**: 서버는 청첩장 데이터만 저장하고, 디자인 렌더링은 프론트(Next.js)가 전담한다. 서버는 HTML/CSS를 들고 있지 않는다.
- **입력 검증 = 서버 책임**: 사용자가 보낸 청첩장 JSON을, 선택한 템플릿의 섹션 스키마로 서버가 검증한 뒤 저장한다. (깨진 청첩장이 하객에게 노출되는 것을 막기 위함)
- **템플릿 관리 위치 = 현재 이 서버**: 관리자가 이 서버를 통해 템플릿을 등록·관리한다. 추후 별도 서비스로 분리 가능성은 열어둔다.
- **프론트 = 별도 Next.js 레포**: 이 레포는 REST API만 제공한다.
- **청첩장 공개 = 고유 URL/슬러그**: 하객은 로그인 없이 고유 URL로 열람한다.
- **알고 받아들이는 제약**: 새 템플릿 추가 = DB 등록 + 프론트 컴포넌트 배포. `template_id ↔ 프론트 컴포넌트` 매핑은 항상 동기화돼야 한다.

## 2026-07-01 — 서비스 분석/기획서(SA) 작성

- 기획자 관점의 서비스 분석/기획서를 [docs/SA-service-analysis.md](./SA-service-analysis.md)에 작성. 문제정의·페르소나·유저 시나리오·기능 정의서(우선순위)·정책·KPI·범위 정리.
- 기존 결정(ARCHITECTURE.md, ADR-001/002) 기반이며 시스템 상세는 링크로 참조. 페르소나·KPI 목표 수치·기능 우선순위는 잠정 제안값으로 추후 조정 필요.

## 2026-07-01 — 핵심 ADR 결정 확정 (승인됨)

상세 배경·대안·트레이드오프는 각 ADR 문서 참고.

- **[ADR-001](./adr/ADR-001-image-storage-s3-presigned.md) 사진 저장**: 원본은 **AWS S3**, 업로드는 **presigned URL로 클라이언트 → S3 직접 업로드**. 서버는 바이너리를 중계하지 않고 청첩장 데이터엔 S3 키/URL만 저장. 트레이드오프: 서버가 파일 내용을 못 봐 업로드 후 검증 필요, 고아 객체 정리 정책 필요.
- **[ADR-002](./adr/ADR-002-template-schema-jsonschema-jsonb.md) 템플릿 스키마·검증·저장**: 섹션 스키마는 **JSON Schema 표준**으로 정의(템플릿 메타데이터에 저장, 라이브러리로 검증), 사용자 입력값은 **PostgreSQL jsonb 단일 컬럼**에 저장. 검증은 저장 시점. 스키마 버전 관리는 후속 과제. 트레이드오프: DB 무결성 약함(앱 검증 신뢰 전제), 관리자 등록 스키마의 메타 검증 필요.
- **[ADR-003](./adr/ADR-003-authentication-jwt-oauth2.md) 인증/인가**: 인증은 **JWT(access+refresh)** 무상태, 로그인 수단은 **소셜(OAuth2)+자체 이메일/비밀번호 둘 다**, 하객 공개는 **조회 GET만 비인증 공개 + 무작위 슬러그 + 발행(published) 상태만 노출**, 권한은 **USER/ADMIN** 구분(템플릿 관리는 ADMIN). 트레이드오프: JWT 무효화 어려움(refresh 회전 보완), 소셜+자체 account linking 필요, 공개 조회의 민감정보 노출(무작위 슬러그·noindex로 완화).

## 2026-07-13 — Spring Security + JWT 인증 구현 (ADR-003/004 구현)

ADR-003/004 결정을 코드로 구현. 새 결정 없이 구현 세부만 확정.

- **서명 방식 = HS256(대칭키)**. secret은 `${JWT_SECRET}`(최소 32바이트) 환경변수 주입. 만료: **access 30분(1800초) / refresh 14일(1209600초)**. RS256은 초기엔 과하다고 보고 보류.
- **Security 설정**([config/SecurityConfig.java](../src/main/java/smally/server/config/SecurityConfig.java)): 세션 `STATELESS`, csrf·formLogin·httpBasic·logout 비활성, 현재 `anyRequest().permitAll()`(엔드포인트 정비 후 인가 규칙 축소 예정). `JwtAuthenticationFilter`가 `Bearer` 토큰을 파싱해 `SecurityContext`를 채움(principal=userId, 권한=`ROLE_{role}`).
- **OAuth2는 미구현**(자체 이메일/비밀번호만 우선). 소셜 로그인은 후속.
- **refresh 저장 = Redis, userId당 1개**(ADR-004). refresh 원문이 아닌 **SHA-256 해시**로 저장·대조. login=발급+저장, `/refresh`=대조 후 회전, `/logout`=키 삭제. 현재 **다중 기기 세션 미지원**(userId 키 덮어씀) — 후속 과제.
- **auth API**: `POST /api/auth/signup|login|refresh|logout` 구현([domain/auth](../src/main/java/smally/server/domain/auth)). `/users/me`·OAuth2 엔드포인트는 아직 없음.
- **전역 예외 처리**([core/exception](../src/main/java/smally/server/core/exception)): `BusinessException`+`ErrorCode` enum → `@RestControllerAdvice`가 명세서 §0.3 포맷(code/message)으로 응답(중복 이메일 409, 자격/토큰 불일치 401, 검증 실패 400).

## 2026-07-14 — OAuth 소셜 로그인 구현 (ADR-003 소셜 부분 구현)

설계: [specs/2026-07-14-oauth-login-design.md](./superpowers/specs/2026-07-14-oauth-login-design.md), 계획: [plans/2026-07-14-oauth-login.md](./superpowers/plans/2026-07-14-oauth-login.md). 상세 결정은 [ADR-003](./adr/ADR-003-authentication-jwt-oauth2.md) "2026-07-14 구현 시 확정 사항" 참고.

- **토큰 릴레이 방식**: `POST /api/auth/v1/oauth/{provider}`(kakao|google|naver), body `{accessToken}` → provider userinfo 조회 후 자체 JWT 발급(기존 `TokenResponse` 재사용). 포트 `OauthClient` + provider별 어댑터(`domain/auth/oauth/client`), `OauthClientResolver`, DB 트랜잭션은 `OauthUserLinker`로 분리(외부 HTTP 호출은 트랜잭션 밖).
- **account linking = 이메일 검증 시에만 자동 링킹**, 미검증+기존계정=거부(409), 이메일 미제공=거부. 스키마 변경 없음(`OauthAccount`, `User` 그대로).
- **Boot 4.1 주의**: `RestClient.Builder` 빈이 자동 등록 안 됨 → `config/RestClientConfig`에서 직접 제공(+ connect 2s/read 5s 타임아웃, `SimpleClientHttpRequestFactory`). Jackson 3.x는 `tools.jackson.databind` 네임스페이스.
- **후속 과제**: token-audience 검증(#2), Naver `emailVerified=true` 가정 공식문서 확인, 동시 최초 로그인 unique 레이스 처리, provider 콘솔에서 이메일 필수 동의 설정.
- **git 주의**: 구현 중 커밋 `5a8771e`가 템플릿 설계 문서와 OAuth 파일 일부를 혼재해 커밋(단독 빌드 불가) — 정리 필요(사용자 처리 예정).
