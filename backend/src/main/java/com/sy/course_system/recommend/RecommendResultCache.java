package com.sy.course_system.recommend;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;

/**
 * 推荐结果 v2 缓存：逻辑过期、分布式锁、有界构建和轻量降级的统一状态机。
 *
 * 任何未持有 Redis 锁的请求都不会单独回源；Redis 故障时也只能通过本 JVM 的
 * single-flight 与有界线程池构建，避免故障分支重新放大上游流量。
 */
@Component
public class RecommendResultCache {

    private static final Logger log = LoggerFactory.getLogger(RecommendResultCache.class);

    /**
     * 版本比较和缓存写入必须在 Redis 内原子执行。否则强失效可能穿插在版本检查与 SET 之间，
     * 让旧构建短暂重新可见；事后的补偿删除还可能误删另一个构建写入的新值。
     *
     * ARGV[2] 由 RedisTemplate 的生产 value serializer 序列化，Lua 只做字节透传，
     * 因此后续仍可按 RecommendCacheEntry 正常反序列化。
     */
    private static final DefaultRedisScript<Long> WRITE_IF_VERSION_MATCHES_SCRIPT = new DefaultRedisScript<>("""
            local currentVersion = redis.call('get', KEYS[1])
            if not currentVersion then
                currentVersion = '0'
            end
            if currentVersion ~= ARGV[1] then
                return 0
            end
            redis.call('set', KEYS[2], ARGV[2], 'PX', ARGV[3])
            return 1
            """, Long.class);

    private enum LookupState {
        FRESH,
        STALE,
        MISS
    }

    private enum LockState {
        ACQUIRED,
        BUSY,
        UNAVAILABLE
    }

    private record CacheLookup(LookupState state, HybridRecommendResponseDTO data,
            long userVersion, boolean redisAvailable) {
        private static CacheLookup unavailable() {
            return new CacheLookup(LookupState.MISS, null, 0L, false);
        }
    }

    private record BuildLock(LockState state, String token) {
        private static BuildLock acquired(String token) {
            return new BuildLock(LockState.ACQUIRED, token);
        }

        private static BuildLock busy() {
            return new BuildLock(LockState.BUSY, null);
        }

        private static BuildLock unavailable() {
            return new BuildLock(LockState.UNAVAILABLE, null);
        }
    }

    /**
     * 表示本次 single-flight 没有获得安全构建条件。等待请求应走降级，而不是把它记录成构建失败。
     */
    private static final class BuildSkippedException extends RuntimeException {
        private BuildSkippedException(String message) {
            super(message, null, false, false);
        }
    }

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RecommendScoreNormalizer scoreNormalizer;
    private final RecommendProperties properties;
    private final RecommendRedisLock redisLock;
    private final RecommendBuildCoordinator buildCoordinator;
    private final RecommendCacheMetrics metrics;

    public RecommendResultCache(RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            RecommendScoreNormalizer scoreNormalizer,
            RecommendProperties properties,
            RecommendRedisLock redisLock,
            RecommendBuildCoordinator buildCoordinator,
            RecommendCacheMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.scoreNormalizer = scoreNormalizer;
        this.properties = properties;
        this.redisLock = redisLock;
        this.buildCoordinator = buildCoordinator;
        this.metrics = metrics;
    }

    public HybridRecommendResponseDTO getOrBuild(Long userId, RecommendCacheType type,
            Supplier<HybridRecommendResponseDTO> builder,
            Supplier<HybridRecommendResponseDTO> degradedSupplier) {
        String cacheKey = type.cacheKey(userId);
        CacheLookup lookup = read(cacheKey, userId);
        if (lookup.state() == LookupState.FRESH) {
            metrics.event("hit");
            return lookup.data();
        }
        if (lookup.state() == LookupState.STALE) {
            metrics.event("stale_hit");
            startCoordinatedBuild(userId, cacheKey, type, builder, false);
            return lookup.data();
        }

        metrics.event("miss");
        return awaitOrDegrade(userId,
                startCoordinatedBuild(userId, cacheKey, type, builder, true), degradedSupplier);
    }

    public void delete(Long userId, RecommendCacheType type) {
        deleteKey(type.cacheKey(userId));
    }

    private CompletableFuture<HybridRecommendResponseDTO> startCoordinatedBuild(Long userId, String cacheKey,
            RecommendCacheType type, Supplier<HybridRecommendResponseDTO> builder,
            boolean allowLocalBuildWithoutRedis) {
        try {
            return buildCoordinator.submit(cacheKey,
                    () -> executeBuild(userId, cacheKey, type, builder, allowLocalBuildWithoutRedis));
        } catch (RejectedExecutionException ex) {
            metrics.event("refresh_rejected");
            return CompletableFuture.failedFuture(ex);
        }
    }

    private HybridRecommendResponseDTO executeBuild(Long userId, String cacheKey, RecommendCacheType type,
            Supplier<HybridRecommendResponseDTO> builder, boolean allowLocalBuildWithoutRedis) {
        CacheLookup beforeLock = read(cacheKey, userId);
        if (beforeLock.state() == LookupState.FRESH) {
            return beforeLock.data();
        }

        if (!beforeLock.redisAvailable()) {
            return buildLocallyOrSkip(cacheKey, builder, allowLocalBuildWithoutRedis);
        }

        String lockKey = type.lockKey(userId);
        BuildLock lock = tryAcquire(lockKey);
        if (lock.state() == LockState.ACQUIRED) {
            return buildWhileLocked(userId, cacheKey, lockKey, lock.token(), type, builder,
                    allowLocalBuildWithoutRedis);
        }
        if (lock.state() == LockState.UNAVAILABLE) {
            return buildLocallyOrSkip(cacheKey, builder, allowLocalBuildWithoutRedis);
        }

        CacheLookup waited = waitForCache(cacheKey, userId);
        if (waited.state() != LookupState.MISS) {
            return waited.data();
        }
        if (!waited.redisAvailable()) {
            return buildLocallyOrSkip(cacheKey, builder, allowLocalBuildWithoutRedis);
        }

        // 等待后只再竞争一次。仍锁忙时结束本次 flight，严禁无锁执行昂贵 builder。
        BuildLock retryLock = tryAcquire(lockKey);
        if (retryLock.state() == LockState.ACQUIRED) {
            return buildWhileLocked(userId, cacheKey, lockKey, retryLock.token(), type, builder,
                    allowLocalBuildWithoutRedis);
        }
        if (retryLock.state() == LockState.UNAVAILABLE) {
            return buildLocallyOrSkip(cacheKey, builder, allowLocalBuildWithoutRedis);
        }
        throw new BuildSkippedException("recommend build lock remained busy");
    }

    private HybridRecommendResponseDTO buildWhileLocked(Long userId, String cacheKey, String lockKey,
            String token, RecommendCacheType type, Supplier<HybridRecommendResponseDTO> builder,
            boolean allowLocalBuildWithoutRedis) {
        try {
            CacheLookup checked = read(cacheKey, userId);
            if (checked.state() == LookupState.FRESH) {
                return checked.data();
            }
            if (!checked.redisAvailable()) {
                return buildLocallyOrSkip(cacheKey, builder, allowLocalBuildWithoutRedis);
            }
            return buildAndMaybeWrite(userId, cacheKey, type, checked.userVersion(), builder, true);
        } finally {
            release(lockKey, token);
        }
    }

    private HybridRecommendResponseDTO buildLocallyOrSkip(String cacheKey,
            Supplier<HybridRecommendResponseDTO> builder, boolean allowLocalBuildWithoutRedis) {
        if (!allowLocalBuildWithoutRedis) {
            throw new BuildSkippedException("Redis unavailable while refreshing stale recommend cache");
        }
        return buildAndMaybeWrite(null, cacheKey, null, 0L, builder, false);
    }

    private HybridRecommendResponseDTO buildAndMaybeWrite(Long userId, String cacheKey, RecommendCacheType type,
            long userVersion, Supplier<HybridRecommendResponseDTO> builder, boolean write) {
        long started = System.nanoTime();
        try {
            HybridRecommendResponseDTO result = builder.get();
            if (result == null) {
                throw new IllegalStateException("recommend builder returned null");
            }
            scoreNormalizer.fillRecommendScores(result);
            if (write) {
                write(cacheKey, userId, type, userVersion, result);
            }
            return result;
        } catch (RuntimeException ex) {
            metrics.event("build_failed");
            // 日志归属于实际执行的构建任务，避免共享同一 Future 的等待者重复打印同一异常。
            log.warn("Recommend cache build failed for key {}", cacheKey, ex);
            throw ex;
        } finally {
            metrics.buildCompleted(System.nanoTime() - started);
        }
    }

    private HybridRecommendResponseDTO awaitOrDegrade(Long userId,
            CompletableFuture<HybridRecommendResponseDTO> future,
            Supplier<HybridRecommendResponseDTO> degradedSupplier) {
        try {
            return future.get(properties.cache().initialBuildWaitMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            metrics.event("wait_timeout");
            return degrade(userId, degradedSupplier);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return degrade(userId, degradedSupplier);
        } catch (ExecutionException ex) {
            // 构建异常已由实际执行 builder 的线程记录；线程池拒绝则由指标表达。
            // 等待者只负责降级，避免高并发时为同一失败重复输出完整堆栈。
            return degrade(userId, degradedSupplier);
        } catch (RuntimeException ex) {
            log.warn("Unexpected failure while waiting for recommend cache build", ex);
            return degrade(userId, degradedSupplier);
        }
    }

    private HybridRecommendResponseDTO degrade(Long userId,
            Supplier<HybridRecommendResponseDTO> degradedSupplier) {
        metrics.event("degraded");
        try {
            HybridRecommendResponseDTO degraded = degradedSupplier.get();
            if (degraded == null) {
                return new HybridRecommendResponseDTO(userId, List.of());
            }
            scoreNormalizer.fillRecommendScores(degraded);
            return degraded;
        } catch (RuntimeException ex) {
            log.warn("Recommend degraded supplier failed, returning an empty result", ex);
            return new HybridRecommendResponseDTO(userId, List.of());
        }
    }

    private CacheLookup read(String cacheKey, Long userId) {
        List<Object> values;
        try {
            values = redisTemplate.opsForValue().multiGet(List.of(cacheKey,
                    RecommendCacheKeys.USER_VERSION_PREFIX + userId));
        } catch (SerializationException ex) {
            evictBadCache(cacheKey, ex);
            return new CacheLookup(LookupState.MISS, null, 0L, true);
        } catch (RuntimeException ex) {
            metrics.event("redis_error");
            log.warn("Redis read failed for recommend cache key {}", cacheKey, ex);
            return CacheLookup.unavailable();
        }

        Object cached = values == null || values.isEmpty() ? null : values.get(0);
        long userVersion = values == null || values.size() < 2 ? 0L : parseVersion(values.get(1));
        if (cached == null) {
            return new CacheLookup(LookupState.MISS, null, userVersion, true);
        }

        RecommendCacheEntry entry;
        try {
            entry = cached instanceof RecommendCacheEntry typed
                    ? typed
                    : objectMapper.convertValue(cached, RecommendCacheEntry.class);
        } catch (IllegalArgumentException ex) {
            evictBadCache(cacheKey, ex);
            return new CacheLookup(LookupState.MISS, null, userVersion, true);
        }
        if (entry.schemaVersion() != RecommendCacheEntry.CURRENT_SCHEMA_VERSION || entry.data() == null) {
            evictBadCache(cacheKey, new IllegalArgumentException("unsupported recommend cache schema"));
            return new CacheLookup(LookupState.MISS, null, userVersion, true);
        }

        scoreNormalizer.fillRecommendScores(entry.data());
        boolean stale = entry.logicalExpireAt() <= System.currentTimeMillis()
                || entry.userVersion() != userVersion;
        return new CacheLookup(stale ? LookupState.STALE : LookupState.FRESH,
                entry.data(), userVersion, true);
    }

    private CacheLookup waitForCache(String cacheKey, Long userId) {
        CacheLookup last = new CacheLookup(LookupState.MISS, null, 0L, true);
        for (int index = 0; index < properties.cache().waitRetryTimes(); index++) {
            sleep(properties.cache().waitMillis());
            last = read(cacheKey, userId);
            if (last.state() != LookupState.MISS || !last.redisAvailable()) {
                return last;
            }
        }
        return last;
    }

    private void write(String cacheKey, Long userId, RecommendCacheType type, long userVersion,
            HybridRecommendResponseDTO result) {
        RecommendProperties.Cache cache = properties.cache();
        long jitterBound = type.jitterMinutes(cache);
        long jitter = jitterBound == 0 ? 0 : ThreadLocalRandom.current().nextLong(jitterBound + 1);
        long logicalTtlMinutes = type.logicalTtlMinutes(cache) + jitter;
        long now = System.currentTimeMillis();
        RecommendCacheEntry entry = new RecommendCacheEntry(RecommendCacheEntry.CURRENT_SCHEMA_VERSION,
                now, now + TimeUnit.MINUTES.toMillis(logicalTtlMinutes), userVersion, result);
        long physicalTtlMinutes = logicalTtlMinutes + cache.staleRetentionMinutes();
        try {
            Long written = redisTemplate.execute(
                    WRITE_IF_VERSION_MATCHES_SCRIPT,
                    List.of(RecommendCacheKeys.USER_VERSION_PREFIX + userId, cacheKey),
                    userVersion,
                    entry,
                    TimeUnit.MINUTES.toMillis(physicalTtlMinutes));
            if (!Long.valueOf(1L).equals(written)) {
                metrics.event("build_discarded");
            }
        } catch (RuntimeException ex) {
            metrics.event("redis_error");
            log.warn("Failed to write recommend cache key {}", cacheKey, ex);
        }
    }

    private BuildLock tryAcquire(String lockKey) {
        try {
            Optional<String> token = redisLock.acquire(lockKey, properties.cache().buildLockTtlSeconds());
            if (token.isPresent()) {
                metrics.event("lock_acquired");
                return BuildLock.acquired(token.get());
            }
            metrics.event("lock_busy");
            return BuildLock.busy();
        } catch (RuntimeException ex) {
            metrics.event("redis_error");
            log.warn("Failed to acquire recommend build lock {}", lockKey, ex);
            return BuildLock.unavailable();
        }
    }

    private void release(String lockKey, String token) {
        try {
            redisLock.release(lockKey, token);
        } catch (RuntimeException ex) {
            metrics.event("redis_error");
            log.warn("Failed to release recommend build lock {}", lockKey, ex);
        }
    }

    private long parseVersion(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private void evictBadCache(String cacheKey, RuntimeException ex) {
        log.warn("Invalid recommend cache value for key {}, evicting it", cacheKey, ex);
        deleteKey(cacheKey);
    }

    private void deleteKey(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ex) {
            metrics.event("redis_error");
            log.warn("Failed to delete recommend cache key {}", key, ex);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
