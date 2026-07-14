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

        // 네이버는 별도 검증 플래그를 제공하지 않으나, 네이버 계정 이메일은 provider가 검증한 값이므로 true로 간주한다.
        return new OauthUserInfo(OauthProvider.NAVER, providerUserId, email, nickname, true);
    }
}
