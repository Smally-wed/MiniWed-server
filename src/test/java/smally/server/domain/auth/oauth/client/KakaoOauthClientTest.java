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
