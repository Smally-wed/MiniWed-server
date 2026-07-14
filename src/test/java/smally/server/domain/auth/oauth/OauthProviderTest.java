package smally.server.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.AuthException;

class OauthProviderTest {

    @Test
    void from_대소문자_무시하고_파싱한다() {
        assertThat(OauthProvider.from("kakao")).isEqualTo(OauthProvider.KAKAO);
        assertThat(OauthProvider.from("GOOGLE")).isEqualTo(OauthProvider.GOOGLE);
        assertThat(OauthProvider.from("Naver")).isEqualTo(OauthProvider.NAVER);
    }

    @Test
    void from_지원하지_않는_값이면_예외() {
        assertThatThrownBy(() -> OauthProvider.from("facebook"))
                .isInstanceOf(AuthException.class)
                .satisfies(e -> assertThat(((AuthException) e).getErrorCode())
                        .isEqualTo(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER));
    }

    @Test
    void from_null이면_예외() {
        assertThatThrownBy(() -> OauthProvider.from(null))
                .isInstanceOf(AuthException.class);
    }
}
