package smally.server.domain.auth.oauth.client;

import tools.jackson.databind.JsonNode;
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
