package smally.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;
import smally.server.domain.auth.oauth.OauthClientResolver;
import smally.server.domain.auth.oauth.client.GoogleOauthClient;
import smally.server.domain.auth.oauth.client.KakaoOauthClient;
import smally.server.domain.auth.oauth.client.NaverOauthClient;

class RestClientConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    RestClientConfig.class,
                    KakaoOauthClient.class,
                    GoogleOauthClient.class,
                    NaverOauthClient.class,
                    OauthClientResolver.class);

    @Test
    void restClientBuilder_빈이_제공되어_OAuth_클라이언트가_주입된다() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeanNamesForType(RestClient.Builder.class)).hasSize(1);
            assertThat(context).hasSingleBean(OauthClientResolver.class);
            assertThat(context).hasSingleBean(KakaoOauthClient.class);
            assertThat(context).hasSingleBean(GoogleOauthClient.class);
            assertThat(context).hasSingleBean(NaverOauthClient.class);
        });
    }
}
