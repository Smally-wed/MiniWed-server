package smally.server.domain.auth.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

@RedisHash("refreshToken")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    private Long userId;

    private String tokenHash;

    @TimeToLive
    private Long ttlSeconds;

    @Builder
    private RefreshToken(Long userId, String tokenHash, Long ttlSeconds) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.ttlSeconds = ttlSeconds;
    }
}
