package com.sy.course_system.recommend;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
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

    private static final Logger log = LoggerFactory.getLogger(RecommendResultCache.class);

    private enum BuildLockState {
        ACQUIRED,
        BUSY,
        UNAVAILABLE
    }

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
     * Redis 任意环节失败都不阻断主流程；但 Redis 正常且锁被占用时仍等待缓存，保留防击穿能力。
     */
    public HybridRecommendResponseDTO getOrBuildWithCache(String cacheKey, String lockKey, long ttlMinutes,
            Supplier<HybridRecommendResponseDTO> builder) {
        HybridRecommendResponseDTO cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        BuildLockState lockState = tryAcquireBuildLock(lockKey);
        if (lockState == BuildLockState.UNAVAILABLE) {
            HybridRecommendResponseDTO result = builder.get();
            scoreNormalizer.fillRecommendScores(result);
            return result;
        }

        if (lockState == BuildLockState.BUSY) {
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
            try {
                redisTemplate.delete(lockKey);
            } catch (RuntimeException e) {
                log.warn("Failed to release build lock for key {}", lockKey, e);
            }
        }
    }

    /**
     * 读取缓存并兼容 Map 反序列化场景，自动补齐展示分。
     * Redis 读失败视为 cache miss，直接回源构建。
     */
    public HybridRecommendResponseDTO readCache(String cacheKey) {
        Object cached;
        try {
            cached = redisTemplate.opsForValue().get(cacheKey);
        } catch (SerializationException e) {
            evictBadCache(cacheKey, "Failed to deserialize cache value for key {}, treating as cache miss", e);
            return null;
        } catch (RuntimeException e) {
            log.warn("Redis read failed for cache key {}, treating as cache miss", cacheKey, e);
            return null;
        }
        if (cached == null) {
            return null;
        }
        if (cached instanceof HybridRecommendResponseDTO dto) {
            scoreNormalizer.fillRecommendScores(dto);
            return dto;
        }
        try {
            HybridRecommendResponseDTO dto = objectMapper.convertValue(cached, HybridRecommendResponseDTO.class);
            scoreNormalizer.fillRecommendScores(dto);
            return dto;
        } catch (IllegalArgumentException e) {
            evictBadCache(cacheKey, "Failed to convert cache value for key {}, treating as cache miss", e);
            return null;
        }
    }

    /**
     * 写入缓存，写入前补齐展示分。写入失败只记录 warn，不影响返回结果。
     */
    public void writeCache(String cacheKey, HybridRecommendResponseDTO value, long ttlMinutes) {
        scoreNormalizer.fillRecommendScores(value);
        try {
            redisTemplate.opsForValue().set(cacheKey, value, ttlMinutes, TimeUnit.MINUTES);
        } catch (RuntimeException e) {
            log.warn("Failed to write cache for key {}, result still returned", cacheKey, e);
        }
    }

    /**
     * 删除缓存 key。删除失败只记录 warn，不阻断主流程。
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException e) {
            log.warn("Failed to delete cache key {}", key, e);
        }
    }

    private BuildLockState tryAcquireBuildLock(String lockKey) {
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(lockKey, "1",
                    recommendProperties.cache().buildLockTtlSeconds(),
                    TimeUnit.SECONDS);
            return Boolean.TRUE.equals(ok) ? BuildLockState.ACQUIRED : BuildLockState.BUSY;
        } catch (RuntimeException e) {
            log.warn("Failed to acquire build lock for key {}, proceeding without lock", lockKey, e);
            return BuildLockState.UNAVAILABLE;
        }
    }

    private HybridRecommendResponseDTO waitForCache(String cacheKey) {
        int retryTimes = Math.max(recommendProperties.cache().waitRetryTimes(), 0);
        long waitMillis = Math.max(recommendProperties.cache().waitMillis(), 0L);
        for (int i = 0; i < retryTimes; i++) {
            sleepQuietly(waitMillis);
            HybridRecommendResponseDTO waited = readCache(cacheKey);
            if (waited != null) {
                return waited;
            }
        }
        return null;
    }

    private void evictBadCache(String cacheKey, String message, RuntimeException ex) {
        log.warn(message, cacheKey, ex);
        try {
            redisTemplate.delete(cacheKey);
        } catch (RuntimeException deleteEx) {
            log.warn("Failed to delete bad cache key {}", cacheKey, deleteEx);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
