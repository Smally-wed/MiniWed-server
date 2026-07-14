package smally.server.domain.auth.oauth;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.AuthException;
import smally.server.domain.auth.oauth.client.OauthClient;

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
