# OAuth 소셜 로그인/회원가입 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 카카오·구글·네이버 소셜 로그인을 토큰 릴레이 방식으로 추가하여, provider access token으로 사용자를 인증하고 우리 자체 JWT를 발급한다.

**Architecture:** 프론트가 provider SDK로 얻은 access token을 `POST /api/auth/v1/oauth/{provider}`로 전달 → 서버가 provider userinfo API로 검증·정규화 → `OauthAccount(provider, providerUserId)` 조회 후 재로그인/이메일 자동링킹/신규가입 분기 → 기존 `AuthService.issueTokens` 재사용해 JWT 발급. 포트(`OauthClient`)·도메인 개념은 `domain/auth/oauth`에, provider HTTP 어댑터는 `domain/auth/oauth/client`에 둔다.

**Tech Stack:** Spring Boot 4.1, Java 25, Spring Security(무상태 JWT), Spring `RestClient`, Jackson `JsonNode`, JUnit5 + Mockito + `MockRestServiceServer`.

## Global Constraints

- 스키마 변경 없음 — `User`(email not-null+unique), `OauthAccount`(unique {provider, provider_user_id}) 그대로 사용.
- build.gradle 의존성 추가 없음 — `RestClient`(webmvc), Jackson, spring-test 모두 기존 보유. oauth2-client 스타터 불필요.
- provider 문자열 저장 규칙: `OauthProvider.name()`("KAKAO"/"GOOGLE"/"NAVER")를 DB와 조회에 일관 사용.
- 이메일 미제공(null/blank) → 가입 거부(`OAUTH_EMAIL_REQUIRED`).
- account linking: 소셜 이메일 == 기존 User 이메일이면 그 User에 `OauthAccount` 연결, 없으면 새 User(password=null, role=USER).
- 신규 소셜 User는 자체 로그인 불가(password null → 기존 `login()`이 이미 null 방어함).
- 커밋 메시지 스타일: 기존 관례 유지(`feat : ...` 등).

---

### Task 1: 도메인 개념 + 에러 코드 (OauthProvider, OauthUserInfo, OauthClient 포트)

**Files:**
- Modify: `src/main/java/smally/server/core/exception/ErrorCode.java`
- Create: `src/main/java/smally/server/domain/auth/oauth/OauthProvider.java`
- Create: `src/main/java/smally/server/domain/auth/oauth/OauthUserInfo.java`
- Create: `src/main/java/smally/server/domain/auth/oauth/OauthClient.java`
- Test: `src/test/java/smally/server/domain/auth/oauth/OauthProviderTest.java`

**Interfaces:**
- Produces:
  - `enum OauthProvider { KAKAO, GOOGLE, NAVER; static OauthProvider from(String value); }`
  - `record OauthUserInfo(OauthProvider provider, String providerUserId, String email, String nickname)`
  - `interface OauthClient { OauthProvider provider(); OauthUserInfo fetchUserInfo(String accessToken); }`
  - `ErrorCode.UNSUPPORTED_OAUTH_PROVIDER`, `ErrorCode.OAUTH_PROVIDER_ERROR`, `ErrorCode.OAUTH_EMAIL_REQUIRED`

- [ ] **Step 1: ErrorCode에 OAuth 에러 3종 추가**

`ErrorCode.java`의 enum 상수 목록 마지막 항목(`USER_NOT_FOUND(...)`) 뒤에 추가한다. `USER_NOT_FOUND` 줄 끝의 `;`를 `,`로 바꾸고 아래 3줄을 이어 붙인 뒤 마지막 줄에 `;`를 둔다.

```java
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "사용자를 찾을 수 없습니다."),
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "지원하지 않는 소셜 로그인 제공자입니다."),
    OAUTH_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "BAD_GATEWAY", "소셜 로그인 제공자와 통신 중 오류가 발생했습니다."),
    OAUTH_EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "소셜 계정의 이메일 제공 동의가 필요합니다.");
```

- [ ] **Step 2: 실패하는 테스트 작성 (OauthProvider.from)**

`src/test/java/smally/server/domain/auth/oauth/OauthProviderTest.java`:

```java
package smally.server.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.AuthException;

class OauthProviderTest {

    @Test
    void from_대소문자_무시하고_파싱한다() {
        assertThat(OauthProvider.from("kakao")).isEqualTo(OauthProvider.KAKAO);
        assertThat(OauthProvider.from("GOOGLE")).isEqualTo(OauthProvider.GOOGLE);
        assertThat(OauthProvider.from("Naver")).isEqualTo(OauthProvider.NAVER);
    }

    @Test
    void from_지원하지_않는_값이면_예외() {
        assertThatThrownBy(() -> OauthProvider.from("facebook"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER));
    }

    @Test
    void from_null이면_예외() {
        assertThatThrownBy(() -> OauthProvider.from(null))
                .isInstanceOf(AuthException.class);
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests "smally.server.domain.auth.oauth.OauthProviderTest"`
Expected: 컴파일 실패 (`OauthProvider` 심볼 없음).

- [ ] **Step 4: OauthProvider / OauthUserInfo / OauthClient 구현**

`src/main/java/smally/server/domain/auth/oauth/OauthProvider.java`:

```java
package smally.server.domain.auth.oauth;

import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.AuthException;

public enum OauthProvider {
    KAKAO,
    GOOGLE,
    NAVER;

    public static OauthProvider from(String value) {
        if (value != null) {
            for (OauthProvider provider : values()) {
                if (provider.name().equalsIgnoreCase(value)) {
                    return provider;
                }
            }
        }
        throw new AuthException(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
    }
}
```

`src/main/java/smally/server/domain/auth/oauth/OauthUserInfo.java`:

```java
package smally.server.domain.auth.oauth;

public record OauthUserInfo(
        OauthProvider provider,
        String providerUserId,
        String email,
        String nickname
) {
}
```

`src/main/java/smally/server/domain/auth/oauth/OauthClient.java`:

```java
package smally.server.domain.auth.oauth;

/**
 * provider access token으로 provider의 사용자 정보를 조회해 정규화한다.
 * 구현체는 외부 HTTP 호출만 담당하고 도메인 로직을 갖지 않는다.
 */
public interface OauthClient {

    OauthProvider provider();

    OauthUserInfo fetchUserInfo(String accessToken);
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "smally.server.domain.auth.oauth.OauthProviderTest"`
Expected: PASS (3개 테스트 통과).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/smally/server/core/exception/ErrorCode.java \
        src/main/java/smally/server/domain/auth/oauth/OauthProvider.java \
        src/main/java/smally/server/domain/auth/oauth/OauthUserInfo.java \
        src/main/java/smally/server/domain/auth/oauth/OauthClient.java \
        src/test/java/smally/server/domain/auth/oauth/OauthProviderTest.java
git commit -m "feat : OAuth provider enum, userinfo, 포트, 에러코드 추가"
```

---

### Task 2: KakaoOauthClient (어댑터)

**Files:**
- Create: `src/main/java/smally/server/domain/auth/oauth/client/KakaoOauthClient.java`
- Test: `src/test/java/smally/server/domain/auth/oauth/client/KakaoOauthClientTest.java`

**Interfaces:**
- Consumes: `OauthClient`, `OauthProvider.KAKAO`, `OauthUserInfo`, `ErrorCode.OAUTH_PROVIDER_ERROR`
- Produces: `class KakaoOauthClient implements OauthClient` — 생성자 `KakaoOauthClient(RestClient.Builder builder)`. userinfo URL `https://kapi.kakao.com/v2/user/me`.

- [ ] **Step 1: 실패하는 테스트 작성 (MockRestServiceServer)**

`src/test/java/smally/server/domain/auth/oauth/client/KakaoOauthClientTest.java`:

```java
package smally.server.domain.auth.oauth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import smally.server.core.exception.exceptions.AuthException;
import smally.server.domain.auth.oauth.OauthProvider;
import smally.server.domain.auth.oauth.OauthUserInfo;

class KakaoOauthClientTest {

    @Test
    void fetchUserInfo_카카오_응답을_정규화한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoOauthClient client = new KakaoOauthClient(builder);

        String json = """
                {
                  "id": 1234567890,
                  "kakao_account": {
                    "email": "user@kakao.com",
                    "profile": { "nickname": "카카오유저" }
                  }
                }
                """;
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token123"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        OauthUserInfo info = client.fetchUserInfo("token123");

        assertThat(info.provider()).isEqualTo(OauthProvider.KAKAO);
        assertThat(info.providerUserId()).isEqualTo("1234567890");
        assertThat(info.email()).isEqualTo("user@kakao.com");
        assertThat(info.nickname()).isEqualTo("카카오유저");
    }

    @Test
    void fetchUserInfo_이메일_미동의면_email이_null() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoOauthClient client = new KakaoOauthClient(builder);

        String json = """
                { "id": 42, "kakao_account": { "profile": { "nickname": "익명" } } }
                """;
        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        OauthUserInfo info = client.fetchUserInfo("token123");

        assertThat(info.email()).isNull();
    }

    @Test
    void fetchUserInfo_provider_오류면_AuthException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KakaoOauthClient client = new KakaoOauthClient(builder);

        server.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchUserInfo("token123"))
                .isInstanceOf(AuthException.class);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "smally.server.domain.auth.oauth.client.KakaoOauthClientTest"`
Expected: 컴파일 실패 (`KakaoOauthClient` 없음).

- [ ] **Step 3: KakaoOauthClient 구현**

`src/main/java/smally/server/domain/auth/oauth/client/KakaoOauthClient.java`:

```java
package smally.server.domain.auth.oauth.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.AuthException;
import smally.server.domain.auth.oauth.OauthClient;
import smally.server.domain.auth.oauth.OauthProvider;
import smally.server.domain.auth.oauth.OauthUserInfo;

@Component
public class KakaoOauthClient implements OauthClient {

    private static final String USERINFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;

    public KakaoOauthClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Override
    public OauthProvider provider() {
        return OauthProvider.KAKAO;
    }

    @Override
    public OauthUserInfo fetchUserInfo(String accessToken) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(USERINFO_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AuthException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
        if (body == null) {
            throw new AuthException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }

        String providerUserId = body.path("id").asText(null);
        JsonNode account = body.path("kakao_account");
        String email = account.path("email").asText(null);
        String nickname = account.path("profile").path("nickname").asText(null);

        return new OauthUserInfo(OauthProvider.KAKAO, providerUserId, email, nickname);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "smally.server.domain.auth.oauth.client.KakaoOauthClientTest"`
Expected: PASS (3개 통과).

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/smally/server/domain/auth/oauth/client/KakaoOauthClient.java \
        src/test/java/smally/server/domain/auth/oauth/client/KakaoOauthClientTest.java
git commit -m "feat : 카카오 OAuth 클라이언트 추가"
```

---

### Task 3: GoogleOauthClient (어댑터)

**Files:**
- Create: `src/main/java/smally/server/domain/auth/oauth/client/GoogleOauthClient.java`
- Test: `src/test/java/smally/server/domain/auth/oauth/client/GoogleOauthClientTest.java`

**Interfaces:**
- Consumes: `OauthClient`, `OauthProvider.GOOGLE`, `OauthUserInfo`, `ErrorCode.OAUTH_PROVIDER_ERROR`
- Produces: `class GoogleOauthClient implements OauthClient` — 생성자 `GoogleOauthClient(RestClient.Builder builder)`. userinfo URL `https://openidconnect.googleapis.com/v1/userinfo`.

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/smally/server/domain/auth/oauth/client/GoogleOauthClientTest.java`:

```java
package smally.server.domain.auth.oauth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import smally.server.domain.auth.oauth.OauthProvider;
import smally.server.domain.auth.oauth.OauthUserInfo;

class GoogleOauthClientTest {

    @Test
    void fetchUserInfo_구글_응답을_정규화한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleOauthClient client = new GoogleOauthClient(builder);

        String json = """
                {
                  "sub": "108120915",
                  "email": "user@gmail.com",
                  "email_verified": true,
                  "name": "구글유저"
                }
                """;
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token123"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        OauthUserInfo info = client.fetchUserInfo("token123");

        assertThat(info.provider()).isEqualTo(OauthProvider.GOOGLE);
        assertThat(info.providerUserId()).isEqualTo("108120915");
        assertThat(info.email()).isEqualTo("user@gmail.com");
        assertThat(info.nickname()).isEqualTo("구글유저");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "smally.server.domain.auth.oauth.client.GoogleOauthClientTest"`
Expected: 컴파일 실패 (`GoogleOauthClient` 없음).

- [ ] **Step 3: GoogleOauthClient 구현**

`src/main/java/smally/server/domain/auth/oauth/client/GoogleOauthClient.java`:

```java
package smally.server.domain.auth.oauth.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.AuthException;
import smally.server.domain.auth.oauth.OauthClient;
import smally.server.domain.auth.oauth.OauthProvider;
import smally.server.domain.auth.oauth.OauthUserInfo;

@Component
public class GoogleOauthClient implements OauthClient {

    private static final String USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";

    private final RestClient restClient;

    public GoogleOauthClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Override
    public OauthProvider provider() {
        return OauthProvider.GOOGLE;
    }

    @Override
    public OauthUserInfo fetchUserInfo(String accessToken) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(USERINFO_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AuthException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
        if (body == null) {
            throw new AuthException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }

        String providerUserId = body.path("sub").asText(null);
        String email = body.path("email").asText(null);
        String nickname = body.path("name").asText(null);

        return new OauthUserInfo(OauthProvider.GOOGLE, providerUserId, email, nickname);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "smally.server.domain.auth.oauth.client.GoogleOauthClientTest"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/smally/server/domain/auth/oauth/client/GoogleOauthClient.java \
        src/test/java/smally/server/domain/auth/oauth/client/GoogleOauthClientTest.java
git commit -m "feat : 구글 OAuth 클라이언트 추가"
```

---

### Task 4: NaverOauthClient (어댑터)

**Files:**
- Create: `src/main/java/smally/server/domain/auth/oauth/client/NaverOauthClient.java`
- Test: `src/test/java/smally/server/domain/auth/oauth/client/NaverOauthClientTest.java`

**Interfaces:**
- Consumes: `OauthClient`, `OauthProvider.NAVER`, `OauthUserInfo`, `ErrorCode.OAUTH_PROVIDER_ERROR`
- Produces: `class NaverOauthClient implements OauthClient` — 생성자 `NaverOauthClient(RestClient.Builder builder)`. userinfo URL `https://openapi.naver.com/v1/nid/me`. 응답은 `response` 객체로 한 겹 감싸져 있다.

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/smally/server/domain/auth/oauth/client/NaverOauthClientTest.java`:

```java
package smally.server.domain.auth.oauth.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import smally.server.domain.auth.oauth.OauthProvider;
import smally.server.domain.auth.oauth.OauthUserInfo;

class NaverOauthClientTest {

    @Test
    void fetchUserInfo_네이버_응답을_정규화한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NaverOauthClient client = new NaverOauthClient(builder);

        String json = """
                {
                  "resultcode": "00",
                  "message": "success",
                  "response": {
                    "id": "abc-123",
                    "email": "user@naver.com",
                    "nickname": "네이버유저"
                  }
                }
                """;
        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token123"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        OauthUserInfo info = client.fetchUserInfo("token123");

        assertThat(info.provider()).isEqualTo(OauthProvider.NAVER);
        assertThat(info.providerUserId()).isEqualTo("abc-123");
        assertThat(info.email()).isEqualTo("user@naver.com");
        assertThat(info.nickname()).isEqualTo("네이버유저");
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "smally.server.domain.auth.oauth.client.NaverOauthClientTest"`
Expected: 컴파일 실패 (`NaverOauthClient` 없음).

- [ ] **Step 3: NaverOauthClient 구현**

`src/main/java/smally/server/domain/auth/oauth/client/NaverOauthClient.java`:

```java
package smally.server.domain.auth.oauth.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.AuthException;
import smally.server.domain.auth.oauth.OauthClient;
import smally.server.domain.auth.oauth.OauthProvider;
import smally.server.domain.auth.oauth.OauthUserInfo;

@Component
public class NaverOauthClient implements OauthClient {

    private static final String USERINFO_URL = "https://openapi.naver.com/v1/nid/me";

    private final RestClient restClient;

    public NaverOauthClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Override
    public OauthProvider provider() {
        return OauthProvider.NAVER;
    }

    @Override
    public OauthUserInfo fetchUserInfo(String accessToken) {
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(USERINFO_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new AuthException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }
        if (body == null) {
            throw new AuthException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }

        JsonNode response = body.path("response");
        String providerUserId = response.path("id").asText(null);
        String email = response.path("email").asText(null);
        String nickname = response.path("nickname").asText(null);

        return new OauthUserInfo(OauthProvider.NAVER, providerUserId, email, nickname);
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "smally.server.domain.auth.oauth.client.NaverOauthClientTest"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/smally/server/domain/auth/oauth/client/NaverOauthClient.java \
        src/test/java/smally/server/domain/auth/oauth/client/NaverOauthClientTest.java
git commit -m "feat : 네이버 OAuth 클라이언트 추가"
```

---

### Task 5: OauthClientResolver

**Files:**
- Create: `src/main/java/smally/server/domain/auth/oauth/OauthClientResolver.java`
- Test: `src/test/java/smally/server/domain/auth/oauth/OauthClientResolverTest.java`

**Interfaces:**
- Consumes: `List<OauthClient>`, `OauthProvider`, `ErrorCode.UNSUPPORTED_OAUTH_PROVIDER`
- Produces: `class OauthClientResolver { OauthClient resolve(OauthProvider provider); }` — 생성자 `OauthClientResolver(List<OauthClient> clients)`.

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/smally/server/domain/auth/oauth/OauthClientResolverTest.java`:

```java
package smally.server.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import smally.server.core.exception.exceptions.AuthException;

class OauthClientResolverTest {

    private OauthClient stub(OauthProvider provider) {
        return new OauthClient() {
            @Override
            public OauthProvider provider() {
                return provider;
            }

            @Override
            public OauthUserInfo fetchUserInfo(String accessToken) {
                return null;
            }
        };
    }

    @Test
    void resolve_provider에_맞는_클라이언트를_반환한다() {
        OauthClient kakao = stub(OauthProvider.KAKAO);
        OauthClient google = stub(OauthProvider.GOOGLE);
        OauthClientResolver resolver = new OauthClientResolver(List.of(kakao, google));

        assertThat(resolver.resolve(OauthProvider.KAKAO)).isSameAs(kakao);
        assertThat(resolver.resolve(OauthProvider.GOOGLE)).isSameAs(google);
    }

    @Test
    void resolve_등록되지_않은_provider면_예외() {
        OauthClientResolver resolver = new OauthClientResolver(List.of(stub(OauthProvider.KAKAO)));

        assertThatThrownBy(() -> resolver.resolve(OauthProvider.NAVER))
                .isInstanceOf(AuthException.class);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "smally.server.domain.auth.oauth.OauthClientResolverTest"`
Expected: 컴파일 실패 (`OauthClientResolver` 없음).

- [ ] **Step 3: OauthClientResolver 구현**

`src/main/java/smally/server/domain/auth/oauth/OauthClientResolver.java`:

```java
package smally.server.domain.auth.oauth;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.AuthException;

@Component
public class OauthClientResolver {

    private final Map<OauthProvider, OauthClient> clients;

    public OauthClientResolver(List<OauthClient> clients) {
        this.clients = new EnumMap<>(OauthProvider.class);
        for (OauthClient client : clients) {
            this.clients.put(client.provider(), client);
        }
    }

    public OauthClient resolve(OauthProvider provider) {
        OauthClient client = clients.get(provider);
        if (client == null) {
            throw new AuthException(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
        }
        return client;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "smally.server.domain.auth.oauth.OauthClientResolverTest"`
Expected: PASS (2개 통과).

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/smally/server/domain/auth/oauth/OauthClientResolver.java \
        src/test/java/smally/server/domain/auth/oauth/OauthClientResolverTest.java
git commit -m "feat : OAuth 클라이언트 리졸버 추가"
```

---

### Task 6: OauthAccount 조회 + AuthService.oauthLogin (조회/링킹/가입/발급)

**Files:**
- Create: `src/main/java/smally/server/domain/auth/dto/OauthLoginRequest.java`
- Modify: `src/main/java/smally/server/domain/auth/repository/OauthAccountRepository.java`
- Modify: `src/main/java/smally/server/domain/auth/service/AuthService.java`
- Test: `src/test/java/smally/server/domain/auth/service/AuthServiceOauthTest.java`

**Interfaces:**
- Consumes: `OauthClientResolver.resolve(OauthProvider)`, `OauthClient.fetchUserInfo(String)`, `OauthUserInfo`, `OauthProvider`, `AuthService.issueTokens(User)`(기존 private), `ErrorCode.OAUTH_EMAIL_REQUIRED`
- Produces:
  - `record OauthLoginRequest(String accessToken)`
  - `OauthAccountRepository.findByProviderAndProviderUserId(String provider, String providerUserId) : Optional<OauthAccount>`
  - `AuthService.oauthLogin(OauthProvider provider, String accessToken) : TokenResponse`

- [ ] **Step 1: OauthLoginRequest DTO 생성 + 리포지토리 메서드 추가**

`src/main/java/smally/server/domain/auth/dto/OauthLoginRequest.java`:

```java
package smally.server.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OauthLoginRequest(
        @NotBlank String accessToken
) {
}
```

`OauthAccountRepository.java` 를 다음으로 교체(메서드 추가):

```java
package smally.server.domain.auth.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.auth.entity.OauthAccount;

public interface OauthAccountRepository extends JpaRepository<OauthAccount, Long> {

    Optional<OauthAccount> findByProviderAndProviderUserId(String provider, String providerUserId);
}
```

- [ ] **Step 2: 실패하는 테스트 작성 (AuthService.oauthLogin 4분기)**

`src/test/java/smally/server/domain/auth/service/AuthServiceOauthTest.java`:

```java
package smally.server.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.AuthException;
import smally.server.core.jwt.JwtProperties;
import smally.server.core.jwt.JwtTokenProvider;
import smally.server.domain.auth.dto.TokenResponse;
import smally.server.domain.auth.entity.OauthAccount;
import smally.server.domain.auth.oauth.OauthClient;
import smally.server.domain.auth.oauth.OauthClientResolver;
import smally.server.domain.auth.oauth.OauthProvider;
import smally.server.domain.auth.oauth.OauthUserInfo;
import smally.server.domain.auth.repository.OauthAccountRepository;
import smally.server.domain.auth.repository.RefreshTokenRepository;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.enums.UserRole;
import smally.server.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceOauthTest {

    @Mock UserRepository userRepository;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock JwtProperties jwtProperties;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock OauthClientResolver oauthClientResolver;
    @Mock OauthAccountRepository oauthAccountRepository;
    @Mock OauthClient oauthClient;
    @Mock User existingUser;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                null,                    // InternalUserService (oauthLogin 미사용)
                userRepository,
                null,                    // PasswordEncoder (oauthLogin 미사용)
                jwtTokenProvider,
                jwtProperties,
                refreshTokenRepository,
                oauthClientResolver,
                oauthAccountRepository);
    }

    private void stubClient(OauthUserInfo info) {
        when(oauthClientResolver.resolve(OauthProvider.KAKAO)).thenReturn(oauthClient);
        when(oauthClient.fetchUserInfo(anyString())).thenReturn(info);
    }

    private void stubTokenIssue(long userId, UserRole role) {
        when(jwtTokenProvider.createAccessToken(userId, role)).thenReturn("access");
        when(jwtTokenProvider.createRefreshToken(userId)).thenReturn("refresh");
        when(jwtProperties.refreshTokenExpiration()).thenReturn(1209600L);
    }

    @Test
    void oauthLogin_기존_소셜계정이면_그_유저로_토큰발급() {
        stubClient(new OauthUserInfo(OauthProvider.KAKAO, "pid-1", "user@kakao.com", "유저"));
        OauthAccount account = OauthAccount.builder()
                .user(existingUser).provider("KAKAO").providerUserId("pid-1").build();
        when(oauthAccountRepository.findByProviderAndProviderUserId("KAKAO", "pid-1"))
                .thenReturn(Optional.of(account));
        when(existingUser.getId()).thenReturn(10L);
        when(existingUser.getUserRole()).thenReturn(UserRole.USER);
        stubTokenIssue(10L, UserRole.USER);

        TokenResponse result = authService.oauthLogin(OauthProvider.KAKAO, "token123");

        assertThat(result.accessToken()).isEqualTo("access");
        verify(userRepository, never()).save(any());
        verify(oauthAccountRepository, never()).save(any());
    }

    @Test
    void oauthLogin_이메일이_기존유저와_같으면_자동링킹() {
        stubClient(new OauthUserInfo(OauthProvider.KAKAO, "pid-2", "user@kakao.com", "유저"));
        when(oauthAccountRepository.findByProviderAndProviderUserId("KAKAO", "pid-2"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@kakao.com")).thenReturn(Optional.of(existingUser));
        when(existingUser.getId()).thenReturn(20L);
        when(existingUser.getUserRole()).thenReturn(UserRole.USER);
        stubTokenIssue(20L, UserRole.USER);

        TokenResponse result = authService.oauthLogin(OauthProvider.KAKAO, "token123");

        assertThat(result.accessToken()).isEqualTo("access");
        verify(oauthAccountRepository).save(any(OauthAccount.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    void oauthLogin_신규면_유저와_소셜계정_생성() {
        stubClient(new OauthUserInfo(OauthProvider.KAKAO, "pid-3", "new@kakao.com", "신규"));
        when(oauthAccountRepository.findByProviderAndProviderUserId("KAKAO", "pid-3"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@kakao.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(existingUser);
        when(existingUser.getId()).thenReturn(30L);
        when(existingUser.getUserRole()).thenReturn(UserRole.USER);
        stubTokenIssue(30L, UserRole.USER);

        TokenResponse result = authService.oauthLogin(OauthProvider.KAKAO, "token123");

        assertThat(result.accessToken()).isEqualTo("access");
        verify(userRepository).save(any(User.class));
        verify(oauthAccountRepository).save(any(OauthAccount.class));
    }

    @Test
    void oauthLogin_이메일_미제공이면_가입거부() {
        stubClient(new OauthUserInfo(OauthProvider.KAKAO, "pid-4", null, "익명"));

        assertThatThrownBy(() -> authService.oauthLogin(OauthProvider.KAKAO, "token123"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(ErrorCode.OAUTH_EMAIL_REQUIRED));

        verify(userRepository, never()).save(any());
        verify(oauthAccountRepository, never()).save(any());
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew test --tests "smally.server.domain.auth.service.AuthServiceOauthTest"`
Expected: 컴파일 실패 (`AuthService` 생성자 인자 수 불일치, `oauthLogin` 없음).

- [ ] **Step 4: AuthService에 의존성·oauthLogin 추가**

`AuthService.java`를 아래로 교체한다. 기존 필드·메서드는 유지하고 (1) import 추가, (2) 필드 2개 추가, (3) `oauthLogin` + `linkOrCreateUser` 메서드 추가만 반영한 전체 파일이다.

```java
package smally.server.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.exception.exceptions.AuthException;
import smally.server.core.exception.ErrorCode;
import smally.server.core.jwt.JwtProperties;
import smally.server.core.jwt.JwtTokenProvider;
import smally.server.domain.auth.dto.LoginRequest;
import smally.server.domain.auth.dto.SignupResponse;
import smally.server.domain.auth.dto.TokenResponse;
import smally.server.domain.auth.entity.OauthAccount;
import smally.server.domain.auth.entity.RefreshToken;
import smally.server.domain.auth.oauth.OauthClient;
import smally.server.domain.auth.oauth.OauthClientResolver;
import smally.server.domain.auth.oauth.OauthProvider;
import smally.server.domain.auth.oauth.OauthUserInfo;
import smally.server.domain.auth.repository.OauthAccountRepository;
import smally.server.domain.auth.repository.RefreshTokenRepository;
import smally.server.domain.user.dto.UserCreateRequest;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.enums.UserRole;
import smally.server.domain.user.repository.UserRepository;
import smally.server.domain.user.service.InternalUserService;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final InternalUserService internalUserService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OauthClientResolver oauthClientResolver;
    private final OauthAccountRepository oauthAccountRepository;

    public SignupResponse signup(UserCreateRequest request) {
        return SignupResponse.from(internalUserService.createUser(request));
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getPassword() == null
                || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(user);
    }

    @Transactional
    public TokenResponse oauthLogin(OauthProvider provider, String accessToken) {
        OauthClient client = oauthClientResolver.resolve(provider);
        OauthUserInfo info = client.fetchUserInfo(accessToken);

        if (info.email() == null || info.email().isBlank()) {
            throw new AuthException(ErrorCode.OAUTH_EMAIL_REQUIRED);
        }

        User user = oauthAccountRepository
                .findByProviderAndProviderUserId(provider.name(), info.providerUserId())
                .map(OauthAccount::getUser)
                .orElseGet(() -> linkOrCreateUser(provider, info));

        return issueTokens(user);
    }

    private User linkOrCreateUser(OauthProvider provider, OauthUserInfo info) {
        User user = userRepository.findByEmail(info.email())
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(info.email())
                        .password(null)
                        .userRole(UserRole.USER)
                        .nickname(info.nickname())
                        .build()));

        oauthAccountRepository.save(OauthAccount.builder()
                .user(user)
                .provider(provider.name())
                .providerUserId(info.providerUserId())
                .build());

        return user;
    }

    public TokenResponse reissue(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new AuthException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        RefreshToken stored = refreshTokenRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!stored.getTokenHash().equals(sha256(refreshToken))) {
            throw new AuthException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_REFRESH_TOKEN));

        return issueTokens(user);
    }

    public void logout(Long userId) {
        refreshTokenRepository.deleteById(userId);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getUserRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(sha256(refreshToken))
                .ttlSeconds(jwtProperties.refreshTokenExpiration())
                .build());

        return TokenResponse.of(accessToken, refreshToken);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew test --tests "smally.server.domain.auth.service.AuthServiceOauthTest"`
Expected: PASS (4개 통과).

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/smally/server/domain/auth/dto/OauthLoginRequest.java \
        src/main/java/smally/server/domain/auth/repository/OauthAccountRepository.java \
        src/main/java/smally/server/domain/auth/service/AuthService.java \
        src/test/java/smally/server/domain/auth/service/AuthServiceOauthTest.java
git commit -m "feat : OAuth 로그인 서비스(조회/링킹/가입/발급) 추가"
```

---

### Task 7: AuthController OAuth 엔드포인트

**Files:**
- Modify: `src/main/java/smally/server/domain/auth/controller/AuthController.java`
- Test: `src/test/java/smally/server/domain/auth/controller/AuthControllerOauthTest.java`

**Interfaces:**
- Consumes: `AuthService.oauthLogin(OauthProvider, String)`, `OauthProvider.from(String)`, `OauthLoginRequest`, `TokenResponse`, `ApiResponse.of(HttpStatus, T)`
- Produces: `POST /api/auth/v1/oauth/{provider}` → `ApiResponse<TokenResponse>`

- [ ] **Step 1: 실패하는 테스트 작성 (@WebMvcTest)**

`src/test/java/smally/server/domain/auth/controller/AuthControllerOauthTest.java`:

```java
package smally.server.domain.auth.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import smally.server.core.jwt.JwtTokenProvider;
import smally.server.domain.auth.dto.TokenResponse;
import smally.server.domain.auth.oauth.OauthProvider;
import smally.server.domain.auth.service.AuthService;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerOauthTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AuthService authService;
    @MockitoBean JwtTokenProvider jwtTokenProvider;

    @Test
    void oauthLogin_토큰을_반환한다() throws Exception {
        when(authService.oauthLogin(eq(OauthProvider.KAKAO), eq("token123")))
                .thenReturn(TokenResponse.of("access", "refresh"));

        mockMvc.perform(post("/api/auth/v1/oauth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"token123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh"));
    }

    @Test
    void oauthLogin_지원하지_않는_provider면_400() throws Exception {
        mockMvc.perform(post("/api/auth/v1/oauth/facebook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"token123\"}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "smally.server.domain.auth.controller.AuthControllerOauthTest"`
Expected: 실패 (엔드포인트 없음 → 404, 두 테스트 모두 기대 상태와 불일치).

- [ ] **Step 3: AuthController에 엔드포인트 추가**

`AuthController.java`에 import 2개를 추가한다:

```java
import org.springframework.web.bind.annotation.PathVariable;
import smally.server.domain.auth.dto.OauthLoginRequest;
import smally.server.domain.auth.oauth.OauthProvider;
```

그리고 `login` 메서드 아래에 엔드포인트를 추가한다:

```java
    @PostMapping("/v1/oauth/{provider}")
    public ResponseEntity<ApiResponse<TokenResponse>> oauthLogin(
            @PathVariable String provider,
            @Valid @RequestBody OauthLoginRequest request) {
        OauthProvider oauthProvider = OauthProvider.from(provider);
        return ResponseEntity.ok(ApiResponse.of(HttpStatus.OK,
                authService.oauthLogin(oauthProvider, request.accessToken())));
    }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "smally.server.domain.auth.controller.AuthControllerOauthTest"`
Expected: PASS (2개 통과). 두 번째 테스트는 `OauthProvider.from("facebook")`이 `AuthException(UNSUPPORTED_OAUTH_PROVIDER, BAD_REQUEST)`를 던지고 `GlobalExceptionHandler`가 400으로 매핑함을 검증한다.

- [ ] **Step 5: 전체 테스트 실행**

Run: `./gradlew test`
Expected: 신규 OAuth 테스트 전부 PASS. (참고: `ServerApplicationTests` 컨텍스트 로드 테스트는 Postgres/Redis가 떠 있어야 통과한다 — 사전 존재 이슈.)

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/smally/server/domain/auth/controller/AuthController.java \
        src/test/java/smally/server/domain/auth/controller/AuthControllerOauthTest.java
git commit -m "feat : OAuth 로그인 엔드포인트 추가"
```

---

## 실행 시 주의 (환경/훅)

- 이 저장소는 Edit/Write마다 `./gradlew build` 전체를 실행하는 훅이 있고, 빌드에는 Postgres·Redis·`jwt.secret`이 필요하다. 신규 OAuth 테스트는 DB/네트워크가 필요 없지만(순수 Mockito·`MockRestServiceServer`·`@WebMvcTest`), 기존 `ServerApplicationTests`는 컨텍스트 로드를 위해 Postgres/Redis가 필요하다. 구현 중에는 Postgres/Redis를 띄워두는 것을 권장한다.
- provider userinfo 엔드포인트 URL은 각 클라이언트에 상수로 두었다. 운영 전 각 provider 개발자 문서로 최신 URL·응답 필드를 확인하고, 콘솔에서 **이메일을 필수 동의**로 설정해야 한다(카카오/네이버).

## Self-Review

- **Spec coverage:** API 엔드포인트(Task 7), 서버 처리 흐름 4분기(Task 6), 컴포넌트 8종(Task 1·5 도메인/포트/리졸버, Task 2~4 어댑터, Task 6 DTO/서비스), 에러 코드 3종(Task 1), 테스트 4분기+어댑터+컨트롤러(각 Task) 모두 태스크로 매핑됨. 스키마·의존성 무변경 제약 준수.
- **Placeholder scan:** TODO/TBD/"적절히 처리" 없음 — 모든 코드·명령·기대결과 명시.
- **Type consistency:** `OauthProvider`, `OauthUserInfo(provider, providerUserId, email, nickname)`, `OauthClient.fetchUserInfo`, `OauthClientResolver.resolve`, `oauthLogin(OauthProvider, String)`, `findByProviderAndProviderUserId(String, String)`, provider 저장값 `name()` — 전 태스크에서 일관.
