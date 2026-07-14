package smally.server.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.AuthException;
import smally.server.domain.auth.entity.OauthAccount;
import smally.server.domain.auth.oauth.OauthProvider;
import smally.server.domain.auth.oauth.OauthUserInfo;
import smally.server.domain.auth.repository.OauthAccountRepository;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class OauthUserLinkerTest {

    @Mock UserRepository userRepository;
    @Mock OauthAccountRepository oauthAccountRepository;
    @Mock User existingUser;

    OauthUserLinker oauthUserLinker;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        oauthUserLinker = new OauthUserLinker(userRepository, oauthAccountRepository);
    }

    @Test
    void resolveOrLink_기존_소셜계정이_있으면_그_유저를_반환하고_저장하지_않는다() {
        OauthUserInfo info = new OauthUserInfo(OauthProvider.KAKAO, "pid-1", "user@kakao.com", "유저", true);
        OauthAccount account = OauthAccount.builder()
                .user(existingUser).provider("KAKAO").providerUserId("pid-1").build();
        when(oauthAccountRepository.findByProviderAndProviderUserId("KAKAO", "pid-1"))
                .thenReturn(Optional.of(account));

        User result = oauthUserLinker.resolveOrLink(OauthProvider.KAKAO, info);

        assertThat(result).isEqualTo(existingUser);
        verify(userRepository, never()).save(any());
        verify(oauthAccountRepository, never()).save(any());
    }

    @Test
    void resolveOrLink_검증된_이메일이면서_기존유저가_있으면_링킹한다() {
        OauthUserInfo info = new OauthUserInfo(OauthProvider.KAKAO, "pid-2", "user@kakao.com", "유저", true);
        when(oauthAccountRepository.findByProviderAndProviderUserId("KAKAO", "pid-2"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@kakao.com")).thenReturn(Optional.of(existingUser));

        User result = oauthUserLinker.resolveOrLink(OauthProvider.KAKAO, info);

        assertThat(result).isEqualTo(existingUser);
        verify(oauthAccountRepository).save(any(OauthAccount.class));
        verify(userRepository, never()).save(any());
    }

    @Test
    void resolveOrLink_기존유저가_없으면_유저와_소셜계정을_생성한다() {
        OauthUserInfo info = new OauthUserInfo(OauthProvider.KAKAO, "pid-3", "new@kakao.com", "신규", true);
        when(oauthAccountRepository.findByProviderAndProviderUserId("KAKAO", "pid-3"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@kakao.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        User result = oauthUserLinker.resolveOrLink(OauthProvider.KAKAO, info);

        assertThat(result).isEqualTo(existingUser);
        verify(userRepository).save(any(User.class));
        verify(oauthAccountRepository).save(any(OauthAccount.class));
    }

    @Test
    void resolveOrLink_미검증_이메일이고_기존유저가_있으면_예외를_던지고_아무것도_저장하지_않는다() {
        OauthUserInfo info = new OauthUserInfo(OauthProvider.KAKAO, "pid-4", "user@kakao.com", "유저", false);
        when(oauthAccountRepository.findByProviderAndProviderUserId("KAKAO", "pid-4"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@kakao.com")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> oauthUserLinker.resolveOrLink(OauthProvider.KAKAO, info))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(ErrorCode.OAUTH_EMAIL_NOT_VERIFIED));

        verify(userRepository, never()).save(any());
        verify(oauthAccountRepository, never()).save(any());
    }

    @Test
    void resolveOrLink_미검증_이메일이라도_기존유저가_없으면_정상_생성한다() {
        OauthUserInfo info = new OauthUserInfo(OauthProvider.KAKAO, "pid-5", "fresh@kakao.com", "신규", false);
        when(oauthAccountRepository.findByProviderAndProviderUserId("KAKAO", "pid-5"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("fresh@kakao.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(existingUser);

        User result = oauthUserLinker.resolveOrLink(OauthProvider.KAKAO, info);

        assertThat(result).isEqualTo(existingUser);
        verify(userRepository).save(any(User.class));
        verify(oauthAccountRepository).save(any(OauthAccount.class));
    }
}
