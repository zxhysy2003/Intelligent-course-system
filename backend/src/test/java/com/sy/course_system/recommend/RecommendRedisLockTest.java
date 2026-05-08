package com.sy.course_system.recommend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RecommendRedisLockTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private RecommendRedisLock recommendRedisLock;

    @BeforeEach
    void setUp() {
        recommendRedisLock = new RecommendRedisLock(stringRedisTemplate);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void acquireShouldReturnGeneratedTokenWhenLockIsAcquired() {
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        when(valueOperations.setIfAbsent(eq("recommend:lock:user:1"), tokenCaptor.capture(), eq(20L),
                eq(TimeUnit.SECONDS))).thenReturn(true);

        Optional<String> token = recommendRedisLock.acquire("recommend:lock:user:1", 20L);

        assertTrue(token.isPresent());
        assertFalse(token.get().isBlank());
        assertNotNull(tokenCaptor.getValue());
        assertFalse(tokenCaptor.getValue().isBlank());
    }

    @Test
    void acquireShouldReturnEmptyWhenLockIsBusy() {
        when(valueOperations.setIfAbsent(eq("recommend:lock:user:1"), any(String.class), eq(20L),
                eq(TimeUnit.SECONDS))).thenReturn(false);

        Optional<String> token = recommendRedisLock.acquire("recommend:lock:user:1", 20L);

        assertTrue(token.isEmpty());
    }

    @Test
    void releaseShouldReturnTrueWhenRedisDeletesOwnedLock() {
        when(stringRedisTemplate.execute(any(RedisScript.class), eq(List.of("recommend:lock:user:1")), eq("token-1")))
                .thenReturn(1L);

        boolean released = recommendRedisLock.release("recommend:lock:user:1", "token-1");

        assertTrue(released);
        verify(stringRedisTemplate).execute(any(RedisScript.class), eq(List.of("recommend:lock:user:1")), eq("token-1"));
    }

    @Test
    void releaseShouldReturnFalseWhenLockIsExpiredOrOwnedByAnotherWorker() {
        when(stringRedisTemplate.execute(any(RedisScript.class), eq(List.of("recommend:lock:user:1")), eq("token-1")))
                .thenReturn(0L);

        boolean released = recommendRedisLock.release("recommend:lock:user:1", "token-1");

        assertFalse(released);
    }
}
