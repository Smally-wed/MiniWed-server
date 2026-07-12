package smally.server.core.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import smally.server.domain.user.enums.UserRole;

/**
 * JWT(access/refresh) 토큰의 생성과 검증을 담당한다.
 * HS256 대칭키 서명을 사용하며, 서버는 무상태로 동작한다.
 */
@Component
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";
    private static final String ROLE_PREFIX = "ROLE_";

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = properties.accessTokenExpiration();
        this.refreshTokenExpiration = properties.refreshTokenExpiration();
    }

    /** access 토큰: subject=userId, role 클레임 포함. */
    public String createAccessToken(Long userId, UserRole role) {
        return createToken(userId, role, accessTokenExpiration);
    }

    /** refresh 토큰: subject=userId 만 담는다(role 없음). */
    public String createRefreshToken(Long userId) {
        return createToken(userId, null, refreshTokenExpiration);
    }

    private String createToken(Long userId, UserRole role, long expirationSeconds) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key);
        if (role != null) {
            builder.claim(ROLE_CLAIM, role.name());
        }
        return builder.compact();
    }

    /** 서명·만료가 유효하면 true. 유효하지 않으면 예외를 삼키고 false. */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** 토큰의 subject(userId)를 꺼낸다. 유효성은 호출 전에 {@link #validateToken}으로 확인한다. */
    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    /** 유효한 토큰에서 인증 객체를 만든다. principal=userId(String), 권한=ROLE_{role}. */
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        String userId = claims.getSubject();
        String role = claims.get(ROLE_CLAIM, String.class);

        Collection<? extends GrantedAuthority> authorities = (role == null)
                ? List.of()
                : List.of(new SimpleGrantedAuthority(ROLE_PREFIX + role));

        return new UsernamePasswordAuthenticationToken(userId, null, authorities);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
