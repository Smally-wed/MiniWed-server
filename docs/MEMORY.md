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

## 2026-07-17 — MVP 성능·운영 연구 계획 수립 (기능 개발 완료 후 착수)

계획: [plans/2026-07-17-performance-research.md](./superpowers/plans/2026-07-17-performance-research.md). 연구는 미착수이며, 여기 남기는 것은 **연구의 전제로 확정한 요구사항·가정·범위**다. 결과가 나오면 각 ADR에 반영한다.

- **제품 요구사항 확정(기준점)**: 하객 청첩장 **로드 ≤ 1,000ms(절대 조건)**, 청첩장 **생성/저장 ≤ 1,000ms**. 모든 목표 수치는 이 둘에서 역산하며, 요구사항이 바뀌면 전부 재계산한다.
- **예산 분해 결과**: 로드 1,000ms = 네트워크 250 + **서버 조회 API 100** + 프론트 렌더 200 + **이미지 400** + 버퍼 50. **서버 API 몫은 전체의 10%, 이미지가 40%**. 저장 1,000ms 중 **JSON Schema 검증은 10ms(1.5%)**.
- **🔴 이미지 크기 상한 = 300KB**: 이미지 예산 400ms × 모바일 실효 대역폭 5Mbps 역산. **아이폰 원본(3~5MB)은 10Mbps에서도 2.4초라 서버 API가 0ms여도 1초 목표가 깨진다.** 1초 목표의 최대 위협은 서버가 아니라 이미지 전달이다.
- **부하 가정**: 동시 발행 **100건**(하한) × 하객 300명 × 재방문 2.0회 = 60,000 조회 → **피크 20~60 RPS**. Postgres가 힘들어할 규모가 아니다.
- **연구 5개·우선순위**: **E(S3 vs CloudFront) → A(JSON Schema 검증 비용) → B(Redis 직렬화) → C(캐시 전략) → D(검색 인덱싱)**, 총 8.5일(Phase 0 포함). E가 1순위인 이유는 예산의 40%를 쥐고 있고, "이미지 최적화 필수" 결론이 나오면 **리사이징 파이프라인이 새 개발 범위로 들어오기** 때문. A가 C보다 앞인 이유는 A의 "스키마 컴파일 캐싱 필수" 결론이 C가 찾던 진짜 캐싱 대상일 수 있어서.
- **연구 E = ADR-001 후속 조치 종결**: [ADR-001](./adr/ADR-001-image-storage-s3-presigned.md)이 "공개 읽기 제공 방식 결정: S3 공개 읽기 vs CloudFront(권장), **별도 검토**"로 남긴 미결을 데이터로 닫는다. presigned는 **업로드 전용**이고 읽기는 공개 읽기이므로 CDN 캐싱과 서명 URL의 충돌은 **없음**(확인 완료).
- **질문 재설계(중요)**: ① 캐시 연구는 "히트율이 몇 %인가"가 아니라 **"이 규모에서 캐시가 애초에 이득인가, 몇 건부터 필요해지는가"**(100/300/1000건 곡선). 트래픽이 없는 상태에서 절대 히트율은 부하 생성기 파라미터의 함수라 동어반복이 된다. **C1(캐시 없음)이 이기면 "Redis를 넣지 않는다"는 근거 있는 결정**이다. ② 검색 연구는 "ES가 과도한가"(답이 정해져 있음)가 아니라 **"몇 건부터 ES가 필요해지는가"** 임계점 특정.
- **탈락 조건(성능과 무관한 합격/불합격)**: 캐시의 **stale read 0건**(예식 시간이 틀린 청첩장은 치명적 결함), Redis 직렬화의 **패키지 이동 후 역직렬화 성공**(ADR-007로 도메인 재설계 중이라 실제 위험).
- **측정 규율**: A·B=JMH, C·D=k6, E=브라우저/실기기. **`System.nanoTime()` 직접 측정 금지**(JIT 워밍업으로 승자가 뒤집힘). 99.9% 신뢰구간이 겹치면 "차이 없음"이 결론. 모든 연구에 baseline 변형을 둔다.
- **가정 표(G1~G9)가 공통 산출물**: G2~G6(하객 수·조회 분포·재방문율)은 실운영 전까지 확인 불가라 가정으로 진행. 실트래픽이 생기면 **가정만 갈아끼워 재실행**한다.
- **후속 과제**: **연구 D의 검색 대상 미확정**(일반 컬럼 → B-tree vs jsonb 내부 → GIN으로 변형이 갈림, 기능 정의 후 분기 선택). G7(모바일 실효 대역폭)·G8(RTT) Phase 0 실측 후 이미지 목표 재확정. CloudFront egress 단가 확인.
- **범위 밖(별도 과제)**: 프론트 렌더 성능, Actuator/관측성 도입, DB 마이그레이션 도구(현재 `ddl-auto: create-drop`이라 운영 배포 불가 — 별건으로 시급).

## 2026-07-17 — Template 레시피 전환(ADR-007 계획 C) 구현 + 기동 불가 버그 발견

계획: [plans/2026-07-17-template-recipe.md](./superpowers/plans/2026-07-17-template-recipe.md)(개정 1 + 실행 기록). 작업 트리에만 존재(미커밋). `clean build` 59 tests / 0 failures.

- **ADR-007 결정 2를 코드로 이행**: `Template` = `sections`(jsonb, `[{componentUId, options, editable}]` 순서 리스트) + `theme`(jsonb) + 메타. `section_schema`·`variants`·`VariantResponse`·`InternalTemplateService`·`isValidTemplate` 제거. 템플릿 등록 검증이 "스키마 문법"에서 **레시피 유효성**(참조 `componentUId` 존재) + **theme 허용값**(OptionDefinition 카탈로그)으로 바뀌었다. 새 에러코드 `INVALID_TEMPLATE_RECIPE`·`INVALID_TEMPLATE_THEME`.
- **🔴 API 파괴적 변경**: `GET /api/template/v1/variant/{uid}`(VariantResponse) → **`GET /api/template/v1/{uid}`(TemplateResponse 전체 상세)**. 프론트 연동 시 확인 필요.
- **결정: Template 조회에 Redis 캐싱 적용**(`TPL:` 키, TTL 30일, Component와 동일 패턴). **단, 이는 성능 연구 C("이 규모에서 캐시가 애초에 이득인가, C1이 이기면 Redis를 넣지 않는다")의 결론을 앞지른 것이다.** 연구 C 결과에 따라 되돌릴 수 있음을 전제로 진행하기로 결정. 또한 템플릿 수정/삭제가 아직 없어 **캐시 무효화 경로가 없다**(등록 시 적재만) — 수정 기능 추가 시 필수이며, 성능 연구의 탈락 조건 "stale read 0건"과 직결.
- **🔴 `ComponentRepository.findbyComponentUid`(소문자 b) 오타로 서버가 기동 불가 상태였다** — 커밋 `3117208` 이후 계속. Spring Data가 `By` 구분자를 못 찾아 메서드명 전체를 프로퍼티로 해석 → `PropertyReferenceException` → 리포지토리 빈 생성 실패 → ApplicationContext 로드 실패. `findByComponentUId`로 수정(+`ComponentServiceImpl` 호출부). 기록: [TROUBLE-component-repository-query-method-typo.md](./TROUBLE-component-repository-query-method-typo.md)
- **교훈(재발 방지의 핵심)**: 이 버그가 커밋 후 계속 살아있었던 이유는 **`compileTestJava`가 깨져 있어 `test` 태스크에 도달하지 못했고, 따라서 `contextLoads()`가 한 번도 실행되지 않았기 때문**이다. 컴파일 에러 방치 = 단순 부채가 아니라 **회귀 탐지 능력의 상실**. 스테일 테스트는 즉시 정리한다.
- **후속 과제**: 미사용 `INVALID_SECTION_VALUES` 제거. `ComponentResponse.frontendBinding` 네이밍 통일(값·레시피 키는 `componentUId`인데 필드명만 다름). `Component.@Builder`에 `componentType` 부재 → `@Setter` 2단 주입이라 타입 없는 Component 생성 가능. `@DataJpaTest` 슬라이스 도입 시 파생 쿼리 오타를 DB 없이 조기 검출 가능.
- **다음**: 계획 D — Invitation 저장 시 `templateUid` → 섹션별 `componentUId` → `Component.data_schema` → `SchemaValidator.validateData`로 `section_values` 검증 + `selected_options` 검증.
