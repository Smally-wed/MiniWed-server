package smally.server.domain.auth.oauth;

public record OauthUserInfo(
        OauthProvider provider,
        String providerUserId,
        String email,
        String nickname
) {
}
