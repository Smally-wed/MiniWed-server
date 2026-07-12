package smally.server.core.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 {@code jwt.*} 값을 바인딩한다.
 * 만료 시간 단위는 초(second)다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpiration,
        long refreshTokenExpiration
) {
}
