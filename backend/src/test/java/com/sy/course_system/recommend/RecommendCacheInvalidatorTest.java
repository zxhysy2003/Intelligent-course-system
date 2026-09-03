package com.sy.course_system.recommend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.support.RecommendPropertiesFixture;

@ExtendWith(MockitoExtension.class)
class RecommendCacheInvalidatorTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private RecommendCacheInvalidator invalidator;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);
        invalidator = new RecommendCacheInvalidator(stringRedisTemplate, RecommendPropertiesFixture.builder().build());
    }

    @Test
    void strongInvalidationShouldDeleteUserCaches() {
        invalidator.invalidateStrongUserRecommend(1L);

        RedisScript<Long> script = captureScript(List.of(
                "recommend:v2:version:user:1",
                "recommend:v2:user:1",
                "recommend:v2:cold:user:1",
                "recommend:cold:status:user:1"));
        assertTrue(script.getScriptAsString().contains("redis.call('incr', KEYS[1])"));
        assertTrue(script.getScriptAsString().contains("redis.call('del', KEYS[2], KEYS[3], KEYS[4])"));
    }

    @Test
    void onboardingInvalidationShouldDeleteUserCaches() {
        invalidator.invalidateOnboardingRecommend(2L);

        captureScript(List.of(
                "recommend:v2:version:user:2",
                "recommend:v2:user:2",
                "recommend:v2:cold:user:2",
                "recommend:cold:status:user:2"));
    }

    @Test
    void studyInvalidationShouldAtomicallyThrottleAdvanceVersionAndClearColdStatus() {
        invalidator.invalidateStudyUserRecommend(3L);

        ArgumentCaptor<RedisScript<Long>> scriptCaptor = scriptCaptor();
        verify(stringRedisTemplate).execute(scriptCaptor.capture(), eq(List.of(
                "recommend:invalidate:study:user:3",
                "recommend:v2:version:user:3",
                "recommend:cold:status:user:3")), eq("90"));
        String script = scriptCaptor.getValue().getScriptAsString();
        assertTrue(script.contains("'EX', throttleSeconds, 'NX'"));
        assertTrue(script.contains("redis.call('incr', KEYS[2])"));
        assertTrue(script.contains("redis.call('del', KEYS[3])"));
    }

    @Test
    void studyInvalidationShouldBypassThrottleWhenConfiguredNonPositive() {
        RecommendProperties properties = RecommendPropertiesFixture.builder()
                .cache(cache -> cache.studyInvalidateThrottleSeconds(0))
                .build();
        invalidator = new RecommendCacheInvalidator(stringRedisTemplate, properties);

        invalidator.invalidateStudyUserRecommend(5L);

        verify(stringRedisTemplate).execute(any(RedisScript.class), eq(List.of(
                "recommend:invalidate:study:user:5",
                "recommend:v2:version:user:5",
                "recommend:cold:status:user:5")), eq("0"));
    }

    @Test
    void redisFailuresShouldNotEscapeInvalidation() {
        doThrow(new RuntimeException("redis unavailable"))
                .when(stringRedisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertDoesNotThrow(() -> invalidator.invalidateStudyUserRecommend(6L));

        verify(stringRedisTemplate).execute(any(RedisScript.class), eq(List.of(
                "recommend:invalidate:study:user:6",
                "recommend:v2:version:user:6",
                "recommend:cold:status:user:6")), eq("90"));
    }

    @Test
    void invalidationShouldRunAfterTransactionCommitWhenSynchronizationIsActive() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            invalidator.invalidateStrongUserRecommend(7L);

            verify(stringRedisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));

            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }

            captureScript(List.of(
                    "recommend:v2:version:user:7",
                    "recommend:v2:user:7",
                    "recommend:v2:cold:user:7",
                    "recommend:cold:status:user:7"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private RedisScript<Long> captureScript(List<String> keys) {
        ArgumentCaptor<RedisScript<Long>> scriptCaptor = scriptCaptor();
        verify(stringRedisTemplate).execute(scriptCaptor.capture(), eq(keys));
        return scriptCaptor.getValue();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private ArgumentCaptor<RedisScript<Long>> scriptCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(RedisScript.class);
    }
}
