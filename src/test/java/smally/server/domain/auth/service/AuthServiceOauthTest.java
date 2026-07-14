package smally.server.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.AuthException;
import smally.server.core.jwt.JwtProperties;
import smally.server.core.jwt.JwtTokenProvider;
import smally.server.domain.auth.dto.TokenResponse;
import smally.server.domain.auth.entity.OauthAccount;
import smally.server.domain.auth.oauth.OauthClient;
import smally.server.domain.auth.oauth.OauthClientResolver;
import smally.server.domain.auth.oauth.OauthProvider;
import smally.server.domain.auth.oauth.OauthUserInfo;
import smally.server.domain.auth.repository.OauthAccountRepository;
import smally.server.domain.auth.repository.RefreshTokenRepository;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.enums.UserRole;
import smally.server.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceOauthTest {

    @Mock UserRepository userRepository;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock JwtProperties jwtProperties;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock OauthClientResolver oauthClientResolver;
    @Mock OauthAccountRepository oauthAccountRepository;
    @Mock OauthClient oauthClient;
    @Mock User existingUser;

    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                null,                    // InternalUserService (oauthLogin 미사용)
                userRepository,
                null,                    // PasswordEncoder (oauthLogin 미사용)
                jwtTokenProvider,
                jwtProperties,
                refreshTokenRepository,
                oauthClientResolver,
                oauthAccountRepository);
    }

    private void stubClient(OauthUserInfo info) {
        when(oauthClientResolver.resolve(OauthProvider.KAKAO)).thenReturn(oauthClient);
        when(oauthClient.fetchUserInfo(anyString())).thenReturn(info);
    }

    private void stubTokenIssue(long userId, UserRole role) {
        when(jwtTokenProvider.createAccessToken(userId, role)).thenReturn("access");
        when(jwtTokenProvider.createRefreshToken(userId)).thenReturn("refresh");
        when(jwtProperties.refreshTokenExpiration()).thenReturn(1209600L);
    }

    @Test
    void oauthLogin_기존_소셜계정이면_그_유저로_토큰발급() {
        stubClient(new OauthUserInfo(OauthProvider.KAKAO, "pid-1", "user@kakao.com", "유저"));
        OauthAccount account = OauthAccount.builder()
                .user(existingUser).provider("KAKAO").providerUserId("pid-1").build();
        when(oauthAccountRepository.findByProviderAndProviderUserId("KAKAO", "pid-1"))
                .thenReturn(Optional.of(account));
        when(existingUser.getId()).thenReturn(10L);
        when(existingUser.getUserRole()).thenReturn(UserRole.USER);
        stubTokenIssue(10L, UserRole.USER);

        TokenResponse result = authService.oauthLogin(OauthProvider.KAKAO, "token123");

        assertThat(result.accessToken()).isEqualTo("access");
        verify(userRepository, never()).save(any());
        verify(oauthAccountRepository, never()).save(any());
    }

    @Test
    void oauthLogin_이메일이_기존유저와_같으면_자동링킹() {
        stubClient(new OauthUserInfo(OauthProvider.KAKAO, "pid-2", "user@kakao.com", "유저"));
        when(oauthAccountRepository.findByProviderAndProviderUserId("KAKAO", "pid-2"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@kakao.com")).thenReturn(Optional.of(existingUser));
        when(existingUser.getId()).thenReturn(20L);
        when(existingUser.getUserRole()).thenReturn(UserRole.USER);
        stubTokenIssue(20L, UserRole.USER);

        TokenResponse result = authService.oauthLogin(OauthProvider.KAKAO, "token123");

        assertThat(result.accessToken()).isEqualTo("access");
        verify(oauthAccountRepository).save(any(OauthAccount.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    void oauthLogin_신규면_유저와_소셜계정_생성() {
        stubClient(new OauthUserInfo(OauthProvider.KAKAO, "pid-3", "new@kakao.com", "신규"));
        when(oauthAccountRepository.findByProviderAndProviderUserId("KAKAO", "pid-3"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@kakao.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(existingUser);
        when(existingUser.getId()).thenReturn(30L);
        when(existingUser.getUserRole()).thenReturn(UserRole.USER);
        stubTokenIssue(30L, UserRole.USER);

        TokenResponse result = authService.oauthLogin(OauthProvider.KAKAO, "token123");

        assertThat(result.accessToken()).isEqualTo("access");
        verify(userRepository).save(any(User.class));
        verify(oauthAccountRepository).save(any(OauthAccount.class));
    }

    @Test
    void oauthLogin_이메일_미제공이면_가입거부() {
        stubClient(new OauthUserInfo(OauthProvider.KAKAO, "pid-4", null, "익명"));

        assertThatThrownBy(() -> authService.oauthLogin(OauthProvider.KAKAO, "token123"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(ErrorCode.OAUTH_EMAIL_REQUIRED));

        verify(userRepository, never()).save(any());
        verify(oauthAccountRepository, never()).save(any());
    }
}
