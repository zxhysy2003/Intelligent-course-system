package com.sy.course_system.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;
import com.sy.course_system.support.RecommendPropertiesFixture;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class RecommendResultCacheTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private RecommendScoreNormalizer scoreNormalizer;
    @Mock
    private RecommendRedisLock redisLock;

    private RecommendResultCache cache;

    @BeforeEach
    void setUp() {
        RecommendProperties properties = RecommendPropertiesFixture.builder()
                .cache(config -> config.waitRetryTimes(1).waitMillis(0).initialBuildWaitMillis(1000)
                        .regularTtlJitterMinutes(10))
                .build();
        cache = new RecommendResultCache(redisTemplate, new ObjectMapper(), scoreNormalizer, properties,
                redisLock, new RecommendBuildCoordinator(Runnable::run),
                new RecommendCacheMetrics(new SimpleMeterRegistry()));
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);
    }

    @Test
    void freshEntryShouldReturnWithoutLockOrBuilder() {
        HybridRecommendResponseDTO response = response(1L);
        RecommendCacheEntry entry = entry(response, System.currentTimeMillis() + 60_000, 3L);
        when(valueOperations.multiGet(anyList())).thenReturn(Arrays.asList(entry, 3L));
        Supplier<HybridRecommendResponseDTO> builder = mockSupplier();

        HybridRecommendResponseDTO result = cache.getOrBuild(1L, RecommendCacheType.REGULAR,
                builder, () -> response(1L));

        assertSame(response, result);
        verify(builder, never()).get();
        verify(redisLock, never()).acquire(any(), anyLong());
    }

    @Test
    void staleEntryShouldReturnOldValueAndNeverBuildWithoutLock() {
        HybridRecommendResponseDTO stale = response(2L);
        when(valueOperations.multiGet(anyList()))
                .thenReturn(Arrays.asList(entry(stale, System.currentTimeMillis() - 1, 0L), 0L));
        when(redisLock.acquire("recommend:v2:lock:user:2", 20L)).thenReturn(Optional.empty());
        Supplier<HybridRecommendResponseDTO> builder = mockSupplier();

        HybridRecommendResponseDTO result = cache.getOrBuild(2L, RecommendCacheType.REGULAR,
                builder, () -> response(2L));

        assertSame(stale, result);
        verify(builder, never()).get();
    }

    @Test
    void busyLockAfterWaitAndRetryShouldUseDegradedSupplierWithoutBuilding() {
        HybridRecommendResponseDTO degraded = response(3L);
        when(valueOperations.multiGet(anyList())).thenReturn(Arrays.asList(null, 0L));
        when(redisLock.acquire("recommend:v2:lock:user:3", 20L)).thenReturn(Optional.empty());
        Supplier<HybridRecommendResponseDTO> builder = mockSupplier();

        HybridRecommendResponseDTO result = cache.getOrBuild(3L, RecommendCacheType.REGULAR,
                builder, () -> degraded);

        assertSame(degraded, result);
        verify(redisLock, times(2)).acquire("recommend:v2:lock:user:3", 20L);
        verify(builder, never()).get();
        verify(valueOperations, never()).get("recommend:user:3");
    }

    @Test
    void busyLockShouldReturnCacheWrittenDuringWait() {
        HybridRecommendResponseDTO cached = response(30L);
        when(valueOperations.multiGet(anyList())).thenReturn(
                Arrays.asList(null, 0L),
                Arrays.asList(null, 0L),
                Arrays.asList(entry(cached, System.currentTimeMillis() + 60_000, 0L), 0L));
        when(redisLock.acquire("recommend:v2:lock:user:30", 20L)).thenReturn(Optional.empty());
        Supplier<HybridRecommendResponseDTO> builder = mockSupplier();

        HybridRecommendResponseDTO result = cache.getOrBuild(30L, RecommendCacheType.REGULAR,
                builder, () -> response(30L));

        assertSame(cached, result);
        verify(redisLock, times(1)).acquire("recommend:v2:lock:user:30", 20L);
        verify(builder, never()).get();
    }

    @Test
    void queuedBuildShouldNotAcquireRedisLockUntilExecutorStartsIt() {
        RecommendProperties properties = RecommendPropertiesFixture.builder()
                .cache(config -> config.initialBuildWaitMillis(1))
                .build();
        List<Runnable> queuedTasks = new ArrayList<>();
        RecommendResultCache queuedCache = new RecommendResultCache(redisTemplate, new ObjectMapper(),
                scoreNormalizer, properties, redisLock, new RecommendBuildCoordinator(queuedTasks::add),
                new RecommendCacheMetrics(new SimpleMeterRegistry()));
        HybridRecommendResponseDTO degraded = response(33L);
        when(valueOperations.multiGet(anyList())).thenReturn(Arrays.asList(null, 0L));
        when(redisLock.acquire("recommend:v2:lock:user:33", 20L)).thenReturn(Optional.of("token"));

        HybridRecommendResponseDTO result = queuedCache.getOrBuild(33L, RecommendCacheType.REGULAR,
                () -> response(33L), () -> degraded);

        assertSame(degraded, result);
        assertEquals(1, queuedTasks.size());
        verify(redisLock, never()).acquire(any(), anyLong());

        queuedTasks.get(0).run();

        verify(redisLock).acquire("recommend:v2:lock:user:33", 20L);
        verify(redisLock).release("recommend:v2:lock:user:33", "token");
    }

    @Test
    void staleEntryShouldReturnBeforeQueuedRefreshAcquiresLock() {
        RecommendProperties properties = RecommendPropertiesFixture.builder().build();
        List<Runnable> queuedTasks = new ArrayList<>();
        RecommendResultCache queuedCache = new RecommendResultCache(redisTemplate, new ObjectMapper(),
                scoreNormalizer, properties, redisLock, new RecommendBuildCoordinator(queuedTasks::add),
                new RecommendCacheMetrics(new SimpleMeterRegistry()));
        HybridRecommendResponseDTO stale = response(37L);
        when(valueOperations.multiGet(anyList())).thenReturn(
                Arrays.asList(entry(stale, System.currentTimeMillis() - 1, 0L), 0L));
        when(redisLock.acquire("recommend:v2:lock:user:37", 20L)).thenReturn(Optional.of("refresh-token"));

        HybridRecommendResponseDTO result = queuedCache.getOrBuild(37L, RecommendCacheType.REGULAR,
                () -> response(37L), () -> response(37L));

        assertSame(stale, result);
        assertEquals(1, queuedTasks.size());
        verify(redisLock, never()).acquire(any(), anyLong());

        queuedTasks.get(0).run();

        verify(redisLock).acquire("recommend:v2:lock:user:37", 20L);
        verify(redisLock).release("recommend:v2:lock:user:37", "refresh-token");
    }

    @Test
    void joiningSameJvmFlightShouldNotAcquireOrReleaseAnotherToken() throws Exception {
        RecommendProperties properties = RecommendPropertiesFixture.builder()
                .cache(config -> config.initialBuildWaitMillis(10))
                .build();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RecommendResultCache asynchronousCache = new RecommendResultCache(redisTemplate, new ObjectMapper(),
                scoreNormalizer, properties, redisLock, new RecommendBuildCoordinator(executor),
                new RecommendCacheMetrics(new SimpleMeterRegistry()));
        CountDownLatch builderStarted = new CountDownLatch(1);
        CountDownLatch allowBuilderToFinish = new CountDownLatch(1);
        HybridRecommendResponseDTO degraded = response(34L);
        when(valueOperations.multiGet(anyList())).thenReturn(Arrays.asList(null, 0L));
        when(redisLock.acquire("recommend:v2:lock:user:34", 20L)).thenReturn(Optional.of("only-token"));

        try {
            HybridRecommendResponseDTO first = asynchronousCache.getOrBuild(34L, RecommendCacheType.REGULAR,
                    () -> {
                        builderStarted.countDown();
                        try {
                            allowBuilderToFinish.await();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(ex);
                        }
                        return response(34L);
                    },
                    () -> degraded);
            assertTrue(builderStarted.await(1, TimeUnit.SECONDS));
            HybridRecommendResponseDTO second = asynchronousCache.getOrBuild(34L, RecommendCacheType.REGULAR,
                    () -> response(34L), () -> degraded);

            assertSame(degraded, first);
            assertSame(degraded, second);
            verify(redisLock, times(1)).acquire("recommend:v2:lock:user:34", 20L);
            verify(redisLock, never()).release("recommend:v2:lock:user:34", "only-token");

            allowBuilderToFinish.countDown();
            verify(redisLock, timeout(1000).times(1)).release("recommend:v2:lock:user:34", "only-token");
        } finally {
            allowBuilderToFinish.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void redisOutageDuringStaleRefreshShouldKeepOldValueWithoutBuilding() {
        HybridRecommendResponseDTO stale = response(35L);
        when(valueOperations.multiGet(anyList())).thenReturn(
                Arrays.asList(entry(stale, System.currentTimeMillis() - 1, 0L), 0L),
                Arrays.asList(entry(stale, System.currentTimeMillis() - 1, 0L), 0L));
        when(redisLock.acquire("recommend:v2:lock:user:35", 20L))
                .thenThrow(new RuntimeException("redis unavailable"));
        Supplier<HybridRecommendResponseDTO> builder = mockSupplier();

        HybridRecommendResponseDTO result = cache.getOrBuild(35L, RecommendCacheType.REGULAR,
                builder, () -> response(35L));

        assertSame(stale, result);
        verify(builder, never()).get();
        verify(redisLock, never()).release(any(), any());
    }

    @Test
    void recoveredRequestShouldJoinOutageFlightWithoutAcquiringTemporaryLock() throws Exception {
        RecommendProperties properties = RecommendPropertiesFixture.builder()
                .cache(config -> config.initialBuildWaitMillis(10))
                .build();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RecommendBuildCoordinator coordinator = new RecommendBuildCoordinator(executor);
        RecommendResultCache asynchronousCache = new RecommendResultCache(redisTemplate, new ObjectMapper(),
                scoreNormalizer, properties, redisLock, coordinator,
                new RecommendCacheMetrics(new SimpleMeterRegistry()));
        AtomicBoolean redisAvailable = new AtomicBoolean(false);
        CountDownLatch localBuilderStarted = new CountDownLatch(1);
        CountDownLatch allowLocalBuilderToFinish = new CountDownLatch(1);
        HybridRecommendResponseDTO degraded = response(36L);
        when(valueOperations.multiGet(anyList())).thenAnswer(invocation -> {
            if (!redisAvailable.get()) {
                throw new RuntimeException("redis unavailable");
            }
            return Arrays.asList(null, 0L);
        });
        when(redisLock.acquire("recommend:v2:lock:user:36", 20L)).thenReturn(Optional.of("recovered-token"));

        try {
            HybridRecommendResponseDTO first = asynchronousCache.getOrBuild(36L, RecommendCacheType.REGULAR,
                    () -> {
                        localBuilderStarted.countDown();
                        try {
                            allowLocalBuilderToFinish.await();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(ex);
                        }
                        return response(36L);
                    }, () -> degraded);
            assertTrue(localBuilderStarted.await(1, TimeUnit.SECONDS));
            redisAvailable.set(true);

            HybridRecommendResponseDTO joined = asynchronousCache.getOrBuild(36L, RecommendCacheType.REGULAR,
                    () -> response(36L), () -> degraded);

            assertSame(degraded, first);
            assertSame(degraded, joined);
            verify(redisLock, never()).acquire(any(), anyLong());
            verify(redisLock, never()).release(any(), any());

            allowLocalBuilderToFinish.countDown();
            executor.submit(() -> { }).get(1, TimeUnit.SECONDS);

            HybridRecommendResponseDTO rebuilt = asynchronousCache.getOrBuild(36L, RecommendCacheType.REGULAR,
                    () -> response(36L), () -> degraded);

            assertEquals(36L, rebuilt.getUserId());
            verify(redisLock, times(1)).acquire("recommend:v2:lock:user:36", 20L);
            verify(redisLock, times(1)).release("recommend:v2:lock:user:36", "recovered-token");
            verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(
                    "recommend:v2:version:user:36", "recommend:v2:user:36")),
                    eq(0L), any(RecommendCacheEntry.class), anyLong());
        } finally {
            allowLocalBuilderToFinish.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void secondLockAttemptShouldBuildAfterWaitMisses() {
        HybridRecommendResponseDTO built = response(31L);
        when(valueOperations.multiGet(anyList())).thenReturn(Arrays.asList(null, 0L));
        when(redisLock.acquire("recommend:v2:lock:user:31", 20L))
                .thenReturn(Optional.empty(), Optional.of("retry-token"));

        HybridRecommendResponseDTO result = cache.getOrBuild(31L, RecommendCacheType.REGULAR,
                () -> built, () -> response(31L));

        assertSame(built, result);
        verify(redisLock, times(2)).acquire("recommend:v2:lock:user:31", 20L);
        verify(redisLock).release("recommend:v2:lock:user:31", "retry-token");
    }

    @Test
    void rejectedLocalBuildShouldUseDegradedSupplierWithoutExecutingBuilder() {
        RecommendProperties properties = RecommendPropertiesFixture.builder().build();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RecommendBuildCoordinator rejectingCoordinator = new RecommendBuildCoordinator(task -> {
            throw new RejectedExecutionException("full");
        });
        RecommendResultCache rejectingCache = new RecommendResultCache(redisTemplate, new ObjectMapper(),
                scoreNormalizer, properties, redisLock, rejectingCoordinator,
                new RecommendCacheMetrics(meterRegistry));
        HybridRecommendResponseDTO degraded = response(32L);
        Supplier<HybridRecommendResponseDTO> builder = mockSupplier();
        when(valueOperations.multiGet(anyList())).thenThrow(new RuntimeException("redis unavailable"));

        HybridRecommendResponseDTO result = rejectingCache.getOrBuild(32L, RecommendCacheType.REGULAR,
                builder, () -> degraded);

        assertSame(degraded, result);
        verify(builder, never()).get();
        assertEquals(1.0d, meterRegistry.get("recommend.cache.events")
                .tag("event", "refresh_rejected").counter().count());
        assertNull(meterRegistry.find("recommend.cache.events")
                .tag("event", "build_failed").counter());
    }

    @Test
    void acquiredLockShouldBuildOnceAndWriteVersionedEntryWithJitteredPhysicalTtl() {
        HybridRecommendResponseDTO built = response(4L);
        when(valueOperations.multiGet(anyList())).thenReturn(Arrays.asList(null, 7L));
        when(redisLock.acquire("recommend:v2:lock:user:4", 20L)).thenReturn(Optional.of("token"));

        HybridRecommendResponseDTO result = cache.getOrBuild(4L, RecommendCacheType.REGULAR,
                () -> built, () -> response(4L));

        assertSame(built, result);
        ArgumentCaptor<RedisScript<Long>> scriptCaptor = scriptCaptor();
        ArgumentCaptor<RecommendCacheEntry> entryCaptor = ArgumentCaptor.forClass(RecommendCacheEntry.class);
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of(
                "recommend:v2:version:user:4", "recommend:v2:user:4")),
                eq(7L), entryCaptor.capture(), ttlCaptor.capture());
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains(
                "if currentVersion ~= ARGV[1] then"));
        assertTrue(scriptCaptor.getValue().getScriptAsString().contains(
                "redis.call('set', KEYS[2], ARGV[2], 'PX', ARGV[3])"));
        assertEquals(7L, entryCaptor.getValue().userVersion());
        assertTrue(ttlCaptor.getValue() >= TimeUnit.MINUTES.toMillis(90L)
                && ttlCaptor.getValue() <= TimeUnit.MINUTES.toMillis(100L));
        verify(redisLock).release("recommend:v2:lock:user:4", "token");
    }

    @Test
    void invalidationDuringBuildShouldDiscardOldVersionWithoutCompensatingDelete() {
        when(valueOperations.multiGet(anyList())).thenReturn(Arrays.asList(null, 0L));
        when(redisLock.acquire("recommend:v2:lock:user:40", 20L)).thenReturn(Optional.of("token"));
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(
                "recommend:v2:version:user:40", "recommend:v2:user:40")),
                eq(0L), any(RecommendCacheEntry.class), anyLong())).thenReturn(0L);

        HybridRecommendResponseDTO result = cache.getOrBuild(40L, RecommendCacheType.REGULAR,
                () -> response(40L), () -> response(40L));

        assertEquals(40L, result.getUserId());
        verify(redisTemplate, never()).delete("recommend:v2:user:40");
    }

    @Test
    void requestTimeoutShouldReturnDegradedWhileBackgroundBuildContinues() throws InterruptedException {
        RecommendProperties properties = RecommendPropertiesFixture.builder()
                .cache(config -> config.initialBuildWaitMillis(10))
                .build();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        RecommendResultCache asynchronousCache = new RecommendResultCache(redisTemplate, new ObjectMapper(),
                scoreNormalizer, properties, redisLock, new RecommendBuildCoordinator(executor),
                new RecommendCacheMetrics(new SimpleMeterRegistry()));
        CountDownLatch buildStarted = new CountDownLatch(1);
        CountDownLatch allowBuildToFinish = new CountDownLatch(1);
        HybridRecommendResponseDTO built = response(41L);
        HybridRecommendResponseDTO degraded = response(41L);
        when(valueOperations.multiGet(anyList())).thenReturn(Arrays.asList(null, 0L));
        when(redisLock.acquire("recommend:v2:lock:user:41", 20L)).thenReturn(Optional.of("token"));

        try {
            HybridRecommendResponseDTO result = asynchronousCache.getOrBuild(41L, RecommendCacheType.REGULAR,
                    () -> {
                        buildStarted.countDown();
                        try {
                            allowBuildToFinish.await();
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(ex);
                        }
                        return built;
                    },
                    () -> degraded);

            assertTrue(buildStarted.await(1, TimeUnit.SECONDS));
            assertSame(degraded, result);
            allowBuildToFinish.countDown();
            verify(redisTemplate, timeout(1000)).execute(any(RedisScript.class), eq(List.of(
                    "recommend:v2:version:user:41", "recommend:v2:user:41")),
                    eq(0L), any(RecommendCacheEntry.class), anyLong());
            verify(redisLock, timeout(1000)).release("recommend:v2:lock:user:41", "token");
        } finally {
            allowBuildToFinish.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void versionMismatchShouldMakeEntryStale() {
        HybridRecommendResponseDTO stale = response(5L);
        when(valueOperations.multiGet(anyList()))
                .thenReturn(Arrays.asList(entry(stale, System.currentTimeMillis() + 60_000, 1L), 2L));
        when(redisLock.acquire("recommend:v2:lock:user:5", 20L)).thenReturn(Optional.empty());

        HybridRecommendResponseDTO result = cache.getOrBuild(5L, RecommendCacheType.REGULAR,
                () -> response(5L), () -> response(5L));

        assertSame(stale, result);
    }

    @Test
    void versionedEntryShouldRoundTripThroughProductionRedisSerializer() {
        RecommendCacheEntry original = entry(response(6L), System.currentTimeMillis() + 60_000, 4L);
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertTrue(restored instanceof RecommendCacheEntry);
        RecommendCacheEntry typed = (RecommendCacheEntry) restored;
        assertEquals(4L, typed.userVersion());
        assertEquals(6L, typed.data().getUserId());
    }

    @SuppressWarnings("unchecked")
    private Supplier<HybridRecommendResponseDTO> mockSupplier() {
        return org.mockito.Mockito.mock(Supplier.class);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private ArgumentCaptor<RedisScript<Long>> scriptCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(RedisScript.class);
    }

    private RecommendCacheEntry entry(HybridRecommendResponseDTO response, long logicalExpireAt, long version) {
        return new RecommendCacheEntry(RecommendCacheEntry.CURRENT_SCHEMA_VERSION,
                System.currentTimeMillis(), logicalExpireAt, version, response);
    }

    private HybridRecommendResponseDTO response(Long userId) {
        return new HybridRecommendResponseDTO(userId, List.of());
    }
}
