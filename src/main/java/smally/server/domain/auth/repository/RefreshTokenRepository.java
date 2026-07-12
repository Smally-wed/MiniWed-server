package smally.server.domain.auth.repository;

import org.springframework.data.repository.CrudRepository;
import smally.server.domain.auth.entity.RefreshToken;

/**
 * RefreshToken은 Redis(@RedisHash)에 저장된다(ADR-004). Spring Data Redis 리포지토리.
 */
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Long> {
}
