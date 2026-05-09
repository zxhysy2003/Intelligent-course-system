package com.sy.course_system.recommend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.support.RecommendPropertiesFixture;

@ExtendWith(MockitoExtension.class)
class RecommendCacheInvalidatorTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private RecommendCacheInvalidator invalidator;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        invalidator = new RecommendCacheInvalidator(redisTemplate, RecommendPropertiesFixture.builder().build());
    }

    @Test
    void strongInvalidationShouldDeleteUserCachesAndScoreMatrix() {
        invalidator.invalidateStrongUserRecommend(1L);

        verify(redisTemplate).delete("recommend:user:1");
        verify(redisTemplate).delete("recommend:cold:user:1");
        verify(redisTemplate).delete("recommend:cold:status:user:1");
        verify(redisTemplate).delete("recommend:score-matrix");
    }

    @Test
    void onboardingInvalidationShouldNotDeleteScoreMatrix() {
        invalidator.invalidateOnboardingRecommend(2L);

        verify(redisTemplate).delete("recommend:user:2");
        verify(redisTemplate).delete("recommend:cold:user:2");
        verify(redisTemplate).delete("recommend:cold:status:user:2");
        verify(redisTemplate, never()).delete("recommend:score-matrix");
    }

    @Test
    void studyInvalidationShouldDeleteWhenThrottleKeysAreAcquired() {
        when(valueOperations.setIfAbsent("recommend:invalidate:study:user:3", "1", 90L, TimeUnit.SECONDS))
                .thenReturn(true);
        when(valueOperations.setIfAbsent("recommend:invalidate:score-matrix", "1", 120L, TimeUnit.SECONDS))
                .thenReturn(true);

        invalidator.invalidateStudyUserRecommend(3L);

        verify(redisTemplate).delete("recommend:user:3");
        verify(redisTemplate).delete("recommend:cold:user:3");
        verify(redisTemplate).delete("recommend:cold:status:user:3");
        verify(redisTemplate).delete("recommend:score-matrix");
    }

    @Test
    void studyInvalidationShouldSkipDeletesWhenThrottleKeysAreBusy() {
        when(valueOperations.setIfAbsent("recommend:invalidate:study:user:4", "1", 90L, TimeUnit.SECONDS))
                .thenReturn(false);
        when(valueOperations.setIfAbsent("recommend:invalidate:score-matrix", "1", 120L, TimeUnit.SECONDS))
                .thenReturn(false);

        invalidator.invalidateStudyUserRecommend(4L);

        verify(redisTemplate, never()).delete("recommend:user:4");
        verify(redisTemplate, never()).delete("recommend:cold:user:4");
        verify(redisTemplate, never()).delete("recommend:cold:status:user:4");
        verify(redisTemplate, never()).delete("recommend:score-matrix");
    }

    @Test
    void studyInvalidationShouldBypassThrottleWhenConfiguredNonPositive() {
        RecommendProperties properties = RecommendPropertiesFixture.builder()
                .cache(cache -> cache.studyInvalidateThrottleSeconds(0)
                        .scoreMatrixInvalidateThrottleSeconds(0))
                .build();
        invalidator = new RecommendCacheInvalidator(redisTemplate, properties);

        invalidator.invalidateStudyUserRecommend(5L);

        verify(valueOperations, never()).setIfAbsent(eq("recommend:invalidate:study:user:5"), eq("1"), anyLong(),
                eq(TimeUnit.SECONDS));
        verify(valueOperations, never()).setIfAbsent(eq("recommend:invalidate:score-matrix"), eq("1"), anyLong(),
                eq(TimeUnit.SECONDS));
        verify(redisTemplate).delete("recommend:user:5");
        verify(redisTemplate).delete("recommend:cold:user:5");
        verify(redisTemplate).delete("recommend:cold:status:user:5");
        verify(redisTemplate).delete("recommend:score-matrix");
    }

    @Test
    void redisFailuresShouldNotEscapeInvalidation() {
        when(valueOperations.setIfAbsent("recommend:invalidate:study:user:6", "1", 90L, TimeUnit.SECONDS))
                .thenThrow(new RuntimeException("redis unavailable"));
        when(valueOperations.setIfAbsent("recommend:invalidate:score-matrix", "1", 120L, TimeUnit.SECONDS))
                .thenThrow(new RuntimeException("redis unavailable"));
        when(redisTemplate.delete("recommend:user:6")).thenThrow(new RuntimeException("delete failed"));

        assertDoesNotThrow(() -> invalidator.invalidateStudyUserRecommend(6L));

        verify(redisTemplate).delete("recommend:cold:user:6");
        verify(redisTemplate).delete("recommend:cold:status:user:6");
        verify(redisTemplate).delete("recommend:score-matrix");
    }

    @Test
    void invalidationShouldRunAfterTransactionCommitWhenSynchronizationIsActive() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            invalidator.invalidateStrongUserRecommend(7L);

            verify(redisTemplate, never()).delete("recommend:user:7");

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            verify(redisTemplate).delete("recommend:user:7");
            verify(redisTemplate).delete("recommend:cold:user:7");
            verify(redisTemplate).delete("recommend:cold:status:user:7");
            verify(redisTemplate).delete("recommend:score-matrix");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
