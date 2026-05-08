package com.sy.course_system.recommend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.SerializationException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;
import com.sy.course_system.support.RecommendPropertiesFixture;

@ExtendWith(MockitoExtension.class)
class RecommendResultCacheTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private RecommendScoreNormalizer scoreNormalizer;
    @Mock
    private RecommendRedisLock recommendRedisLock;

    private RecommendResultCache recommendResultCache;

    @BeforeEach
    void setUp() {
        RecommendProperties recommendProperties = RecommendPropertiesFixture.builder()
                .cache(cache -> cache.waitRetryTimes(1).waitMillis(0L))
                .build();
        recommendResultCache = new RecommendResultCache(redisTemplate, new ObjectMapper(), scoreNormalizer,
                recommendProperties, recommendRedisLock);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getOrBuildShouldWaitForCacheWhenBuildLockIsBusy() {
        HybridRecommendResponseDTO cached = response(1L);
        Supplier<HybridRecommendResponseDTO> builder = mockBuilder();
        when(valueOperations.get("recommend:user:1")).thenReturn(null, cached);
        when(recommendRedisLock.acquire("recommend:lock:user:1", 20L)).thenReturn(Optional.empty());

        HybridRecommendResponseDTO result = recommendResultCache.getOrBuildWithCache(
                "recommend:user:1", "recommend:lock:user:1", 30L, builder);

        assertSame(cached, result);
        verify(builder, never()).get();
        verify(valueOperations, never()).set(eq("recommend:user:1"), any(), eq(30L), eq(TimeUnit.MINUTES));
    }

    @Test
    void getOrBuildShouldBuildAndWriteWhenBuildLockIsBusyAndWaitMisses() {
        HybridRecommendResponseDTO fallback = response(2L);
        Supplier<HybridRecommendResponseDTO> builder = mockBuilder();
        when(builder.get()).thenReturn(fallback);
        when(valueOperations.get("recommend:user:2")).thenReturn(null);
        when(recommendRedisLock.acquire("recommend:lock:user:2", 20L)).thenReturn(Optional.empty());

        HybridRecommendResponseDTO result = recommendResultCache.getOrBuildWithCache(
                "recommend:user:2", "recommend:lock:user:2", 30L, builder);

        assertSame(fallback, result);
        verify(builder).get();
        verify(valueOperations).set("recommend:user:2", fallback, 30L, TimeUnit.MINUTES);
    }

    @Test
    void getOrBuildShouldBuildWithoutWaitingOrWritingWhenBuildLockIsUnavailable() {
        HybridRecommendResponseDTO fallback = response(3L);
        Supplier<HybridRecommendResponseDTO> builder = mockBuilder();
        when(builder.get()).thenReturn(fallback);
        when(valueOperations.get("recommend:user:3")).thenReturn(null);
        when(recommendRedisLock.acquire("recommend:lock:user:3", 20L))
                .thenThrow(new RuntimeException("redis unavailable"));

        HybridRecommendResponseDTO result = recommendResultCache.getOrBuildWithCache(
                "recommend:user:3", "recommend:lock:user:3", 30L, builder);

        assertSame(fallback, result);
        verify(builder).get();
        verify(valueOperations, times(1)).get("recommend:user:3");
        verify(valueOperations, never()).set(eq("recommend:user:3"), any(), eq(30L), eq(TimeUnit.MINUTES));
        verify(recommendRedisLock, never()).release(eq("recommend:lock:user:3"), any());
    }

    @Test
    void getOrBuildShouldReleaseBuildLockWithAcquiredToken() {
        HybridRecommendResponseDTO response = response(9L);
        Supplier<HybridRecommendResponseDTO> builder = mockBuilder();
        when(builder.get()).thenReturn(response);
        when(valueOperations.get("recommend:user:9")).thenReturn(null);
        when(recommendRedisLock.acquire("recommend:lock:user:9", 20L)).thenReturn(Optional.of("token-9"));

        HybridRecommendResponseDTO result = recommendResultCache.getOrBuildWithCache(
                "recommend:user:9", "recommend:lock:user:9", 30L, builder);

        assertSame(response, result);
        verify(recommendRedisLock).release("recommend:lock:user:9", "token-9");
    }

    @Test
    void readCacheShouldEvictBadCacheWhenCannotDeserialize() {
        when(valueOperations.get("recommend:user:4")).thenThrow(new SerializationException("bad cache"));

        HybridRecommendResponseDTO result = recommendResultCache.readCache("recommend:user:4");

        assertNull(result);
        verify(redisTemplate).delete("recommend:user:4");
    }

    @Test
    void readCacheShouldEvictBadCacheWhenShapeIsInvalid() {
        when(valueOperations.get("recommend:user:5")).thenReturn(List.of("bad-cache"));

        HybridRecommendResponseDTO result = recommendResultCache.readCache("recommend:user:5");

        assertNull(result);
        verify(redisTemplate).delete("recommend:user:5");
    }

    @Test
    void readCacheShouldTreatRedisReadFailureAsMissWithoutEvicting() {
        when(valueOperations.get("recommend:user:6")).thenThrow(new RuntimeException("redis unavailable"));

        HybridRecommendResponseDTO result = recommendResultCache.readCache("recommend:user:6");

        assertNull(result);
        verify(redisTemplate, never()).delete("recommend:user:6");
    }

    @Test
    void writeCacheShouldIgnoreRedisWriteFailure() {
        HybridRecommendResponseDTO response = response(7L);
        doThrow(new RuntimeException("redis unavailable"))
                .when(valueOperations).set("recommend:user:7", response, 30L, TimeUnit.MINUTES);

        assertDoesNotThrow(() -> recommendResultCache.writeCache("recommend:user:7", response, 30L));

        verify(scoreNormalizer).fillRecommendScores(response);
    }

    @Test
    void writeCacheShouldPropagateScoreNormalizerFailure() {
        HybridRecommendResponseDTO response = response(8L);
        doThrow(new IllegalStateException("bad score"))
                .when(scoreNormalizer).fillRecommendScores(response);

        assertThrows(IllegalStateException.class,
                () -> recommendResultCache.writeCache("recommend:user:8", response, 30L));
        verify(valueOperations, never()).set(eq("recommend:user:8"), any(), eq(30L), eq(TimeUnit.MINUTES));
    }

    @SuppressWarnings("unchecked")
    private Supplier<HybridRecommendResponseDTO> mockBuilder() {
        return mock(Supplier.class);
    }

    private HybridRecommendResponseDTO response(Long userId) {
        HybridRecommendItemDTO item = new HybridRecommendItemDTO();
        item.setCourseId(10L);
        item.setFinalScore(0.8d);
        item.setRecommendSource(RecommendSource.CF.code());
        return new HybridRecommendResponseDTO(userId, List.of(item));
    }
}
