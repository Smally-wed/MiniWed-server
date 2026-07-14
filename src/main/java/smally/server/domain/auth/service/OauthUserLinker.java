package smally.server.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smally.server.core.exception.ErrorCode;
import smally.server.core.exception.exceptions.AuthException;
import smally.server.domain.auth.entity.OauthAccount;
import smally.server.domain.auth.oauth.OauthProvider;
import smally.server.domain.auth.oauth.OauthUserInfo;
import smally.server.domain.auth.repository.OauthAccountRepository;
import smally.server.domain.user.entity.User;
import smally.server.domain.user.enums.UserRole;
import smally.server.domain.user.repository.UserRepository;

/**
 * 소셜 계정 조회/링킹/신규가입의 DB 트랜잭션 경계.
 * provider 외부 HTTP 호출은 이 경계 밖(AuthService)에서 먼저 수행된다.
 */
@Service
@RequiredArgsConstructor
public class OauthUserLinker {

    private final UserRepository userRepository;
    private final OauthAccountRepository oauthAccountRepository;

    @Transactional
    public User resolveOrLink(OauthProvider provider, OauthUserInfo info) {
        return oauthAccountRepository
                .findByProviderAndProviderUserId(provider.name(), info.providerUserId())
                .map(OauthAccount::getUser)
                .orElseGet(() -> linkOrCreate(provider, info));
    }

    private User linkOrCreate(OauthProvider provider, OauthUserInfo info) {
        User user = userRepository.findByEmail(info.email()).orElse(null);
        if (user != null) {
            // 기존 계정과 이메일이 겹치면, provider가 이메일을 검증한 경우에만 자동 연결한다.
            if (!info.emailVerified()) {
                throw new AuthException(ErrorCode.OAUTH_EMAIL_NOT_VERIFIED);
            }
        } else {
            user = userRepository.save(User.builder()
                    .email(info.email())
                    .password(null)
                    .userRole(UserRole.USER)
                    .nickname(info.nickname())
                    .build());
        }

        oauthAccountRepository.save(OauthAccount.builder()
                .user(user)
                .provider(provider.name())
                .providerUserId(info.providerUserId())
                .build());

        return user;
    }
}
