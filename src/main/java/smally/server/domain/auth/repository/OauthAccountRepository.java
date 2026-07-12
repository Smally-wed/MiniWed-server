package smally.server.domain.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import smally.server.domain.auth.entity.OauthAccount;

public interface OauthAccountRepository extends JpaRepository<OauthAccount, Long> {
}
