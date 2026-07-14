package smally.server.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import smally.server.domain.auth.oauth.client.OauthClient;
import smally.server.domain.auth.oauth.OauthClientResolver;
import smally.server.domain.auth.oauth.OauthProvider;
import smally.server.domain.auth.oauth.OauthUserInfo;
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
    @Mock OauthClient oauthClient;
    @Mock OauthUserLinker oauthUserLinker;
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
                oauthUserLinker);
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
    void oauthLogin_성공하면_linker에_위임하고_토큰을_발급한다() {
        OauthUserInfo info = new OauthUserInfo(OauthProvider.KAKAO, "pid-1", "user@kakao.com", "유저", true);
        stubClient(info);
        when(oauthUserLinker.resolveOrLink(OauthProvider.KAKAO, info)).thenReturn(existingUser);
        when(existingUser.getId()).thenReturn(10L);
        when(existingUser.getUserRole()).thenReturn(UserRole.USER);
        stubTokenIssue(10L, UserRole.USER);

        TokenResponse result = authService.oauthLogin(OauthProvider.KAKAO, "token123");

        assertThat(result.accessToken()).isEqualTo("access");
        verify(oauthUserLinker).resolveOrLink(OauthProvider.KAKAO, info);
    }

    @Test
    void oauthLogin_이메일_null이면_가입거부하고_linker를_호출하지_않는다() {
        stubClient(new OauthUserInfo(OauthProvider.KAKAO, "pid-4", null, "익명", false));

        assertThatThrownBy(() -> authService.oauthLogin(OauthProvider.KAKAO, "token123"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(ErrorCode.OAUTH_EMAIL_REQUIRED));

        verify(oauthUserLinker, never()).resolveOrLink(any(), any());
    }

    @Test
    void oauthLogin_이메일_blank이면_가입거부한다() {
        stubClient(new OauthUserInfo(OauthProvider.KAKAO, "pid-5", "", "익명", false));

        assertThatThrownBy(() -> authService.oauthLogin(OauthProvider.KAKAO, "token123"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(ErrorCode.OAUTH_EMAIL_REQUIRED));

        verify(oauthUserLinker, never()).resolveOrLink(any(), any());
    }

    @Test
    void oauthLogin_providerUserId가_null이면_OAUTH_PROVIDER_ERROR() {
        stubClient(new OauthUserInfo(OauthProvider.KAKAO, null, "user@kakao.com", "익명", true));

        assertThatThrownBy(() -> authService.oauthLogin(OauthProvider.KAKAO, "token123"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(ErrorCode.OAUTH_PROVIDER_ERROR));

        verify(oauthUserLinker, never()).resolveOrLink(any(), any());
    }
}
