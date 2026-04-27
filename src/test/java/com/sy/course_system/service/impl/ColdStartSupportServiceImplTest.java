package com.sy.course_system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

@ExtendWith(MockitoExtension.class)
class ColdStartSupportServiceImplTest {

    @Mock
    private LearningBehaviorMapper learningBehaviorMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private ColdStartSupportServiceImpl coldStartSupportService;

    @Test
    void isColdStartUserShouldThrowWhenUserIdIsNull() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> coldStartSupportService.isColdStartUser(null));

        assertEquals("userId 不能为空", ex.getMessage());
    }

    @Test
    void isColdStartUserShouldReturnCachedValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("recommend:cold:status:user:1")).thenReturn(Boolean.TRUE);

        boolean result = coldStartSupportService.isColdStartUser(1L);

        assertEquals(true, result);
        verify(learningBehaviorMapper, never()).selectColdStartSignal(any());
    }

    @Test
    void isColdStartUserShouldRemainColdStartWhenOnlyViewSignalsExist() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("recommend:cold:status:user:2")).thenReturn(null);
        when(learningBehaviorMapper.selectColdStartSignal(2L)).thenReturn(signal(0L, 0L, 0L, 0L));

        boolean result = coldStartSupportService.isColdStartUser(2L);

        assertEquals(true, result);
        verify(valueOperations).set("recommend:cold:status:user:2", true, 120L, TimeUnit.SECONDS);
    }

    @Test
    void isColdStartUserShouldReturnFalseWhenUserHasFinishedCourse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("recommend:cold:status:user:3")).thenReturn(null);
        when(learningBehaviorMapper.selectColdStartSignal(3L)).thenReturn(signal(2L, 1L, 300L, 1L));

        boolean result = coldStartSupportService.isColdStartUser(3L);

        assertEquals(false, result);
    }

    @Test
    void isColdStartUserShouldReturnFalseWhenTotalStudySecondsReachThreshold() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("recommend:cold:status:user:4")).thenReturn(null);
        when(learningBehaviorMapper.selectColdStartSignal(4L)).thenReturn(signal(1L, 1L, 600L, 0L));

        boolean result = coldStartSupportService.isColdStartUser(4L);

        assertEquals(false, result);
    }

    @Test
    void isColdStartUserShouldReturnFalseWhenStudiedCoursesReachThreshold() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("recommend:cold:status:user:5")).thenReturn(null);
        when(learningBehaviorMapper.selectColdStartSignal(5L)).thenReturn(signal(2L, 2L, 300L, 0L));

        boolean result = coldStartSupportService.isColdStartUser(5L);

        assertEquals(false, result);
        verify(valueOperations).set(eq("recommend:cold:status:user:5"), eq(false), eq(120L), eq(TimeUnit.SECONDS));
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
