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
