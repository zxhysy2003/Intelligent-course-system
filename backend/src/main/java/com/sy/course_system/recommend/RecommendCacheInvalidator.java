package com.sy.course_system.recommend;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sy.course_system.config.RecommendProperties;

/**
 * 推荐缓存失效入口。
 *
 * 业务服务只表达“哪类用户行为影响了推荐”，具体需要删除哪些 recommend:* key
 * 由推荐模块统一维护，避免学习分析、onboarding 等模块散落缓存 key 细节。
 */
@Component
public class RecommendCacheInvalidator {

    private static final Logger log = LoggerFactory.getLogger(RecommendCacheInvalidator.class);

    private static final String STUDY_INVALIDATE_THROTTLE_KEY = "recommend:invalidate:study:user:";

    /**
     * 节流判断、版本递增和冷启动状态删除必须在 Redis 内一次完成。否则旧版本构建可能恰好在
     * 多条命令之间写回缓存，导致已经失效的数据再次变成 fresh。
     */
    private static final DefaultRedisScript<Long> SOFT_INVALIDATE_SCRIPT = new DefaultRedisScript<>("""
            local throttleSeconds = tonumber(ARGV[1])
            if throttleSeconds > 0 then
                local acquired = redis.call('set', KEYS[1], '1', 'EX', throttleSeconds, 'NX')
                if not acquired then
                    return 0
                end
            end
            redis.call('incr', KEYS[2])
            redis.call('del', KEYS[3])
            return 1
            """, Long.class);

    /**
     * 强失效把版本递增和全部相关 key 删除合并成一条原子脚本，禁止并发构建穿插在中间。
     */
    private static final DefaultRedisScript<Long> HARD_INVALIDATE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('incr', KEYS[1])
            redis.call('del', KEYS[2], KEYS[3], KEYS[4])
            return 1
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final RecommendProperties recommendProperties;

    public RecommendCacheInvalidator(StringRedisTemplate stringRedisTemplate,
            RecommendProperties recommendProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.recommendProperties = recommendProperties;
    }

    public void invalidateStrongUserRecommend(Long userId) {
        if (userId == null) {
            return;
        }
        runAfterCommit(() -> hardInvalidate(userId));
    }

    public void invalidateStudyUserRecommend(Long userId) {
        if (userId == null) {
            return;
        }
        runAfterCommit(() -> {
            RecommendProperties.Cache cache = recommendProperties.cache();
            softInvalidate(userId, cache.studyInvalidateThrottleSeconds());
        });
    }

    public void invalidateOnboardingRecommend(Long userId) {
        invalidateStrongUserRecommend(userId);
    }

    private void softInvalidate(Long userId, long throttleSeconds) {
        List<String> keys = List.of(
                STUDY_INVALIDATE_THROTTLE_KEY + userId,
                RecommendCacheKeys.USER_VERSION_PREFIX + userId,
                RecommendCacheKeys.COLD_START_STATUS_PREFIX + userId);
        executeInvalidation(SOFT_INVALIDATE_SCRIPT, keys, "soft", String.valueOf(throttleSeconds));
    }

    private void hardInvalidate(Long userId) {
        List<String> keys = List.of(
                RecommendCacheKeys.USER_VERSION_PREFIX + userId,
                RecommendCacheKeys.V2_REGULAR_PREFIX + userId,
                RecommendCacheKeys.V2_COLD_START_PREFIX + userId,
                RecommendCacheKeys.COLD_START_STATUS_PREFIX + userId);
        executeInvalidation(HARD_INVALIDATE_SCRIPT, keys, "hard");
    }

    private void executeInvalidation(DefaultRedisScript<Long> script, List<String> keys,
            String mode, Object... args) {
        try {
            Long result = stringRedisTemplate.execute(script, keys, args);
            if (result == null) {
                log.warn("Recommend {} invalidation returned no result for keys {}", mode, keys);
            }
        } catch (RuntimeException e) {
            // 脚本内部命令不会被其他客户端穿插；INCR 成功后只执行不依赖 value 类型的 DEL。
            log.warn("Failed to execute atomic recommend {} invalidation for keys {}", mode, keys, e);
        }
    }

    private void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
