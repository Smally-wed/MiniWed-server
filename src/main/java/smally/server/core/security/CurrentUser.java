package smally.server.core.security;

import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.AuthException;

/**
 * JwtAuthenticationFilter가 principal에 넣은 userId 문자열을 꺼낸다.
 * 인증이 없으면 principal이 null이거나 "anonymousUser"다.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Long requireUserId(String principal) {
        try {
            return Long.valueOf(principal);
        } catch (NumberFormatException | NullPointerException e) {
            throw new AuthException(ErrorCode.UNAUTHENTICATED_REQUIRED);
        }
    }
}
