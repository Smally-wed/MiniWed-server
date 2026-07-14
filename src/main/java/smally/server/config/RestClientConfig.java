package smally.server.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Spring Boot 4.1의 모듈화된 자동설정에서는 spring-boot-restclient 모듈이
 * 클래스패스에 없어 RestClient.Builder 빈이 자동 등록되지 않는다.
 * OAuth provider 클라이언트들이 주입받을 수 있도록 직접 제공한다.
 *
 * spring-boot-http-client 모듈(ClientHttpRequestFactoryBuilder/Settings)도
 * 클래스패스에 없으므로, spring-web이 제공하는
 * {@link SimpleClientHttpRequestFactory}로 connect/read 타임아웃을 직접 설정한다.
 */
@Configuration
public class RestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    @Scope("prototype")
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .requestFactory(requestFactory);
    }
}
