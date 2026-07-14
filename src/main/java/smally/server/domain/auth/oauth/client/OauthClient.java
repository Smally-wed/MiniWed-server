package smally.server.domain.auth.oauth.client;

import smally.server.domain.auth.oauth.OauthProvider;
import smally.server.domain.auth.oauth.OauthUserInfo;

/**
 * provider access token으로 provider의 사용자 정보를 조회해 정규화한다.
 * 구현체는 외부 HTTP 호출만 담당하고 도메인 로직을 갖지 않는다.
 */
public interface OauthClient {

    OauthProvider provider();

    OauthUserInfo fetchUserInfo(String accessToken);
}
