package com.sy.course_system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.sy.course_system.dto.recommend.ColdStartSignalDTO;
import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.recommend.RecommendCacheMetrics;
import com.sy.course_system.service.ColdStartDecision;

@ExtendWith(MockitoExtension.class)
class ColdStartSupportServiceImplTest {

    @Mock
    private LearningBehaviorMapper learningBehaviorMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private RecommendCacheMetrics recommendCacheMetrics;

    @InjectMocks
    private ColdStartSupportServiceImpl coldStartSupportService;

    @Test
    void decideShouldThrowWhenUserIdIsNull() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> coldStartSupportService.decide(null));

        assertEquals("userId 不能为空", ex.getMessage());
    }

    @Test
    void decideShouldReturnCachedValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("recommend:cold:status:user:1")).thenReturn(Boolean.TRUE);

        ColdStartDecision result = coldStartSupportService.decide(1L);

        assertEquals(ColdStartDecision.COLD_START, result);
        verify(learningBehaviorMapper, never()).selectColdStartSignal(any());
    }

    @Test
    void decideShouldRemainColdStartWhenOnlyViewSignalsExist() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("recommend:cold:status:user:2")).thenReturn(null);
        when(learningBehaviorMapper.selectColdStartSignal(2L)).thenReturn(signal(0L, 0L, 0L, 0L));

        ColdStartDecision result = coldStartSupportService.decide(2L);

        assertEquals(ColdStartDecision.COLD_START, result);
        verify(valueOperations).set("recommend:cold:status:user:2", true, 120L, TimeUnit.SECONDS);
    }

    @Test
    void decideShouldReturnRegularWhenUserHasFinishedCourse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("recommend:cold:status:user:3")).thenReturn(null);
        when(learningBehaviorMapper.selectColdStartSignal(3L)).thenReturn(signal(2L, 1L, 300L, 1L));

        ColdStartDecision result = coldStartSupportService.decide(3L);

        assertEquals(ColdStartDecision.REGULAR, result);
    }

    @Test
    void decideShouldReturnRegularWhenTotalStudySecondsReachThreshold() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("recommend:cold:status:user:4")).thenReturn(null);
        when(learningBehaviorMapper.selectColdStartSignal(4L)).thenReturn(signal(1L, 1L, 600L, 0L));

        ColdStartDecision result = coldStartSupportService.decide(4L);

        assertEquals(ColdStartDecision.REGULAR, result);
    }

    @Test
    void decideShouldReturnRegularWhenStudiedCoursesReachThreshold() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("recommend:cold:status:user:5")).thenReturn(null);
        when(learningBehaviorMapper.selectColdStartSignal(5L)).thenReturn(signal(2L, 2L, 300L, 0L));

        ColdStartDecision result = coldStartSupportService.decide(5L);

        assertEquals(ColdStartDecision.REGULAR, result);
        verify(valueOperations).set(eq("recommend:cold:status:user:5"), eq(false), eq(120L), eq(TimeUnit.SECONDS));
    }

    @Test
    void decideShouldAvoidDatabaseWhenRedisIsUnavailable() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("recommend:cold:status:user:6"))
                .thenThrow(new RuntimeException("redis unavailable"));

        ColdStartDecision result = coldStartSupportService.decide(6L);

        assertEquals(ColdStartDecision.UNAVAILABLE, result);
        verify(learningBehaviorMapper, never()).selectColdStartSignal(any());
        verify(valueOperations, never()).set(any(), any(), anyLong(), any(TimeUnit.class));
        verify(recommendCacheMetrics).event("redis_error");
    }

    private ColdStartSignalDTO signal(Long effectiveBehaviorCount,
            Long studiedCourseCount,
            Long totalStudySeconds,
            Long finishCount) {
        ColdStartSignalDTO dto = new ColdStartSignalDTO();
        dto.setEffectiveBehaviorCount(effectiveBehaviorCount);
        dto.setStudiedCourseCount(studiedCourseCount);
        dto.setTotalStudySeconds(totalStudySeconds);
        dto.setFinishCount(finishCount);
        return dto;
    }
}
