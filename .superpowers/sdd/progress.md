# OAuth 로그인 SDD 진행 기록

- 실행 방식: 서브에이전트, **커밋 금지** (사용자가 최종 검토 후 직접 커밋)
- BASE 커밋: 6dc564f (HEAD 고정, 태스크마다 이동하지 않음)
- 리뷰 diff 범위: OAuth 관련 파일 경로로 한정

## 태스크
- [x] Task 1: 도메인 개념 + 에러코드 (working tree, review clean; Minor: null-test asserts only exception type)
- [ ] Task 2: KakaoOauthClient
- [ ] Task 3: GoogleOauthClient
- [ ] Task 4: NaverOauthClient
- [ ] Task 5: OauthClientResolver
- [ ] Task 6: OauthLoginRequest + repo + AuthService.oauthLogin
- [ ] Task 7: AuthController 엔드포인트
