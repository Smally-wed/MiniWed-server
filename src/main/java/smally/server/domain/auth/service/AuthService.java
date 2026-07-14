package smally.server.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import smally.server.core.exception.exceptions.AuthException;
import smally.server.core.exception.ErrorCode;
import smally.server.core.jwt.JwtProperties;
import smally.server.core.jwt.JwtTokenProvider;
import smally.server.domain.auth.dto.LoginRequest;
import smally.server.domain.auth.dto.SignupResponse;
import smally.server.domain.auth.dto.TokenResponse;
import smally.server.domain.auth.entity.RefreshToken;
import smally.server.domain.auth.oauth.client.OauthClient;
import smally.server.domain.auth.oauth.OauthClientResolver;
import smally.server.domain.auth.oauth.OauthProvider;
import smally.server.domain.auth.oauth.OauthUserInfo;
import smally.server.domain.auth.repository.RefreshTokenRepository;
import smally.server.domain.user.dto.UserCreateRequest;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.repository.UserRepository;
import smally.server.domain.user.service.InternalUserService;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final InternalUserService internalUserService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OauthClientResolver oauthClientResolver;
    private final OauthUserLinker oauthUserLinker;

    public SignupResponse signup(UserCreateRequest request) {
        return SignupResponse.from(internalUserService.createUser(request));
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getPassword() == null
                || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(user);
    }

    public TokenResponse oauthLogin(OauthProvider provider, String accessToken) {
        OauthClient client = oauthClientResolver.resolve(provider);
        OauthUserInfo info = client.fetchUserInfo(accessToken); // 외부 HTTP 호출: 트랜잭션 밖

        if (info.email() == null || info.email().isBlank()) {
            throw new AuthException(ErrorCode.OAUTH_EMAIL_REQUIRED);
        }

        if (info.providerUserId() == null || info.providerUserId().isBlank()) {
            throw new AuthException(ErrorCode.OAUTH_PROVIDER_ERROR);
        }

        User user = oauthUserLinker.resolveOrLink(provider, info); // DB 작업: 트랜잭션 안
        return issueTokens(user);
    }

    public TokenResponse reissue(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new AuthException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Long userId = jwtTokenProvider.getUserId(refreshToken);
        RefreshToken stored = refreshTokenRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!stored.getTokenHash().equals(sha256(refreshToken))) {
            throw new AuthException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_REFRESH_TOKEN));

        return issueTokens(user);
    }

    public void logout(Long userId) {
        refreshTokenRepository.deleteById(userId);
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getUserRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(sha256(refreshToken))
                .ttlSeconds(jwtProperties.refreshTokenExpiration())
                .build());

        return TokenResponse.of(accessToken, refreshToken);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
