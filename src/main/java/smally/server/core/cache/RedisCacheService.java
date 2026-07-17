package smally.server.core.cache;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public <T> T getCacheData(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (Exception e) {
            log.error("[Cache] redis cache get Failed : {}", e.getMessage());
            return null;
        }
    }

    public void setCacheData(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);

        } catch (Exception e) {
            log.error("[Cache] redis cache set Failed : {}", e.getMessage());
        }
    }

    public void deleteCacheData(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("[Cache] redis cache delete Failed : {}", e.getMessage());
        }
    }
}
