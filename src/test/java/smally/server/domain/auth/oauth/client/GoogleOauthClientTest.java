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
        assertThat(info.emailVerified()).isTrue();
    }

    @Test
    void fetchUserInfo_provider_오류면_AuthException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleOauthClient client = new GoogleOauthClient(builder);

        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchUserInfo("token123"))
                .isInstanceOf(AuthException.class);
    }
}
