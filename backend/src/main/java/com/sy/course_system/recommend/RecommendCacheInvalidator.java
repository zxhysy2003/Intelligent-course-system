package com.sy.course_system.recommend;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
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

    private static final String REGULAR_RECOMMEND_KEY = "recommend:user:";
    private static final String COLD_START_RECOMMEND_KEY = "recommend:cold:user:";
    private static final String COLD_START_STATUS_KEY = "recommend:cold:status:user:";
    private static final String STUDY_INVALIDATE_THROTTLE_KEY = "recommend:invalidate:study:user:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RecommendProperties recommendProperties;

    public RecommendCacheInvalidator(RedisTemplate<String, Object> redisTemplate,
            RecommendProperties recommendProperties) {
        this.redisTemplate = redisTemplate;
        this.recommendProperties = recommendProperties;
    }

    public void invalidateStrongUserRecommend(Long userId) {
        if (userId == null) {
            return;
        }
        runAfterCommit(() -> deleteUserRecommendKeys(userId));
    }

    public void invalidateStudyUserRecommend(Long userId) {
        if (userId == null) {
            return;
        }
        runAfterCommit(() -> {
            RecommendProperties.Cache cache = recommendProperties.cache();
            if (shouldInvalidate(STUDY_INVALIDATE_THROTTLE_KEY + userId,
                    cache.studyInvalidateThrottleSeconds())) {
                deleteUserRecommendKeys(userId);
            }
        });
    }

    public void invalidateOnboardingRecommend(Long userId) {
        if (userId == null) {
            return;
        }
        runAfterCommit(() -> deleteUserRecommendKeys(userId));
    }

    private void deleteUserRecommendKeys(Long userId) {
        deleteKey(REGULAR_RECOMMEND_KEY + userId);
        deleteKey(COLD_START_RECOMMEND_KEY + userId);
        deleteKey(COLD_START_STATUS_KEY + userId);
    }

    private boolean shouldInvalidate(String throttleKey, long throttleSeconds) {
        if (throttleSeconds <= 0) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(throttleKey, "1", throttleSeconds, TimeUnit.SECONDS));
        } catch (RuntimeException e) {
            log.warn("Failed to write recommend invalidation throttle key {}, invalidating anyway", throttleKey, e);
            return true;
        }
    }

    private void deleteKey(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("Failed to delete recommend cache key {}", key, e);
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
