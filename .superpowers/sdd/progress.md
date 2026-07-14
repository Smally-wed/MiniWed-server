# OAuth 로그인 SDD 진행 기록

- 실행 방식: 서브에이전트, **커밋 금지** (사용자가 최종 검토 후 직접 커밋)
- BASE 커밋: 6dc564f (HEAD 고정, 태스크마다 이동하지 않음)
- 리뷰 diff 범위: OAuth 관련 파일 경로로 한정

## 태스크
- [x] Task 1: 도메인 개념 + 에러코드 (working tree, review clean; Minor: null-test asserts only exception type)
- [x] Task 2: KakaoOauthClient (review clean; Minor: asText deprecated→asString in Jackson 3.x, applies to all clients)
- [x] Task 3: GoogleOauthClient (review clean)
- [x] Task 4: NaverOauthClient (review clean)
- [x] Task 5: OauthClientResolver (review clean; Minor: no duplicate-provider guard, not required)
- [x] Task 6: OauthLoginRequest + repo + AuthService.oauthLogin (review clean; Minor: unused eq import in test; concurrent-new-user race out of scope)
- [x] Task 7: AuthController 엔드포인트 (review clean; @WebMvcTest 패키지 Boot4.1 이동 대응)

## 최종 상태 (2026-07-14)
- 7개 태스크 전부 구현·리뷰 완료. 추가로:
  - 회귀 수정: RestClient.Builder 빈(RestClientConfig) — Boot 4.1 부팅 실패 해결
  - 하드닝: #1 이메일 검증 링킹(OauthUserLinker), #3 타임아웃, #4 트랜잭션 밖 I/O
  - 마무리: providerUserId 가드, 구글·네이버 에러경로 테스트
- 전체 OAuth 스코프 테스트 24개 통과. 문서(ADR-003/스펙/MEMORY) 갱신.
- 커밋 안 함(사용자 검토 후 직접). git 상태는 외부 커밋 5a8771e로 조각남 — 사용자 정리 예정.
- 후속 권고: #2 token-audience 검증, Naver emailVerified 가정 확인, 동시로그인 레이스 처리.
