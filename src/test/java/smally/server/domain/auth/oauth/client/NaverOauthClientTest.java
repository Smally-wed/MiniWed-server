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
        assertThat(info.emailVerified()).isTrue();
    }

    @Test
    void fetchUserInfo_provider_오류면_AuthException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NaverOauthClient client = new NaverOauthClient(builder);

        server.expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchUserInfo("token123"))
                .isInstanceOf(AuthException.class);
    }
}
