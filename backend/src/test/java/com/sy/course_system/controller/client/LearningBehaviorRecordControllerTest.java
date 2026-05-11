package com.sy.course_system.controller.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.sy.course_system.common.Result;
import com.sy.course_system.enums.LearnBehaviorType;
import com.sy.course_system.service.LearningBehaviorService;

@ExtendWith(MockitoExtension.class)
class LearningBehaviorRecordControllerTest {

    @Mock
    private LearningBehaviorService learningBehaviorService;

    private LearningBehaviorRecordController controller;

    @BeforeEach
    void setUp() {
        controller = new LearningBehaviorRecordController();
        ReflectionTestUtils.setField(controller, "learningBehaviorService", learningBehaviorService);
    }

    @Test
    void recordBehaviorShouldRejectDirectFinish() {
        Result<?> result = controller.recordBehavior(10L, LearnBehaviorType.FINISH, null);

        assertEquals(400, result.getCode());
        assertEquals("FINISH 行为由学习进度自动生成，不能直接提交", result.getMsg());
        verify(learningBehaviorService, never()).recordBehavior(10L, LearnBehaviorType.FINISH, null);
    }

    @Test
    void recordBehaviorShouldDelegateSupportedBehavior() {
        Result<?> result = controller.recordBehavior(10L, LearnBehaviorType.STUDY, 60);

        assertEquals(200, result.getCode());
        verify(learningBehaviorService).recordBehavior(10L, LearnBehaviorType.STUDY, 60);
    }
}
