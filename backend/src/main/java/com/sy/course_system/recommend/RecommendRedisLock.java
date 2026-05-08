package com.sy.course_system.recommend;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 推荐链路 Redis 短锁。
 *
 * 使用唯一 token 标记锁持有者，释放时通过 Lua 比较 token 后再删除，
 * 避免锁过期后误删其他请求新获得的锁。
 */
@Component
public class RecommendRedisLock {

    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                      return redis.call('del', KEYS[1])
                    else
                      return 0
                    end
                    """,
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RecommendRedisLock(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Optional<String> acquire(String lockKey, long ttlSeconds) {
        String token = UUID.randomUUID().toString();
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, token, ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok) ? Optional.of(token) : Optional.empty();
    }

    public boolean release(String lockKey, String token) {
        Long deleted = stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), token);
        return Long.valueOf(1L).equals(deleted);
    }
}
