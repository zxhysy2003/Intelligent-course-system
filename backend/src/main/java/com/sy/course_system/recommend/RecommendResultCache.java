package com.sy.course_system.recommend;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;

/**
 * 推荐结果缓存组件：读写 Redis 缓存 + 构建锁防击穿逻辑。
 *
 * 行为约定：
 * 1) 命中缓存直接返回；
 * 2) 未命中时通过短锁防并发回源；
 * 3) 未拿到锁时短暂轮询等待，复用其他线程刚写入的结果；
 * 4) 持锁线程在 finally 中释放锁。
 *
 * 自修复：读取时自动补齐展示分，兼容旧缓存/反序列化场景。
 */
@Component
public class RecommendResultCache {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RecommendScoreNormalizer scoreNormalizer;
    private final RecommendProperties recommendProperties;

    public RecommendResultCache(RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            RecommendScoreNormalizer scoreNormalizer,
            RecommendProperties recommendProperties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.scoreNormalizer = scoreNormalizer;
        this.recommendProperties = recommendProperties;
    }

    /**
     * 先读缓存，未命中时通过短锁防击穿后回源构建并写缓存。
     */
    public HybridRecommendResponseDTO getOrBuildWithCache(String cacheKey, String lockKey, long ttlMinutes,
            Supplier<HybridRecommendResponseDTO> builder) {
        HybridRecommendResponseDTO cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        boolean locked = tryAcquireBuildLock(lockKey);
        if (!locked) {
            HybridRecommendResponseDTO waited = waitForCache(cacheKey);
            if (waited != null) {
                return waited;
            }
            HybridRecommendResponseDTO fallback = builder.get();
            writeCache(cacheKey, fallback, ttlMinutes);
            return fallback;
        }

        try {
            HybridRecommendResponseDTO doubleChecked = readCache(cacheKey);
            if (doubleChecked != null) {
                return doubleChecked;
            }
            HybridRecommendResponseDTO result = builder.get();
            writeCache(cacheKey, result, ttlMinutes);
            return result;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 读取缓存并兼容 Map 反序列化场景，自动补齐展示分。
     */
    public HybridRecommendResponseDTO readCache(String cacheKey) {
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached == null) {
            return null;
        }
        if (cached instanceof HybridRecommendResponseDTO dto) {
            scoreNormalizer.fillRecommendScores(dto);
            return dto;
        }
        HybridRecommendResponseDTO dto = objectMapper.convertValue(cached, HybridRecommendResponseDTO.class);
        scoreNormalizer.fillRecommendScores(dto);
        return dto;
    }

    /**
     * 写入缓存，写入前补齐展示分。
     */
    public void writeCache(String cacheKey, HybridRecommendResponseDTO value, long ttlMinutes) {
        scoreNormalizer.fillRecommendScores(value);
        redisTemplate.opsForValue().set(cacheKey, value, ttlMinutes, TimeUnit.MINUTES);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    private boolean tryAcquireBuildLock(String lockKey) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(lockKey, "1",
                recommendProperties.getCache().getBuildLockTtlSeconds(),
                TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    private HybridRecommendResponseDTO waitForCache(String cacheKey) {
        int retryTimes = Math.max(recommendProperties.getCache().getWaitRetryTimes(), 0);
        long waitMillis = Math.max(recommendProperties.getCache().getWaitMillis(), 0L);
        for (int i = 0; i < retryTimes; i++) {
            sleepQuietly(waitMillis);
            HybridRecommendResponseDTO waited = readCache(cacheKey);
            if (waited != null) {
                return waited;
            }
        }
        return null;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
