package smally.server.domain.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import smally.server.core.exception.exceptions.AuthException;

class OauthClientResolverTest {

    private OauthClient stub(OauthProvider provider) {
        return new OauthClient() {
            @Override
            public OauthProvider provider() {
                return provider;
            }

            @Override
            public OauthUserInfo fetchUserInfo(String accessToken) {
                return null;
            }
        };
    }

    @Test
    void resolve_provider에_맞는_클라이언트를_반환한다() {
        OauthClient kakao = stub(OauthProvider.KAKAO);
        OauthClient google = stub(OauthProvider.GOOGLE);
        OauthClientResolver resolver = new OauthClientResolver(List.of(kakao, google));

        assertThat(resolver.resolve(OauthProvider.KAKAO)).isSameAs(kakao);
        assertThat(resolver.resolve(OauthProvider.GOOGLE)).isSameAs(google);
    }

    @Test
    void resolve_등록되지_않은_provider면_예외() {
        OauthClientResolver resolver = new OauthClientResolver(List.of(stub(OauthProvider.KAKAO)));

        assertThatThrownBy(() -> resolver.resolve(OauthProvider.NAVER))
                .isInstanceOf(AuthException.class);
    }
}
