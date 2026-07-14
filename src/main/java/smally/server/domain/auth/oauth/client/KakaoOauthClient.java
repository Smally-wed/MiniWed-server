package smally.server.domain.auth.oauth.client;

import tools.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.AuthException;
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
        boolean emailVerified = account.path("is_email_verified").asBoolean(false);

        return new OauthUserInfo(OauthProvider.KAKAO, providerUserId, email, nickname, emailVerified);
    }
}
