package com.sy.course_system.controller.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sy.course_system.common.Result;
import com.sy.course_system.dto.LearningBehaviorRecordDTO;
import com.sy.course_system.enums.BehaviorRecordOutcome;
import com.sy.course_system.enums.LearnBehaviorType;
import com.sy.course_system.exception.LearningBehaviorEventConflictException;
import com.sy.course_system.service.LearningBehaviorService;
import com.sy.course_system.vo.LearningBehaviorRecordVO;

@ExtendWith(MockitoExtension.class)
class LearningBehaviorRecordControllerTest {

    @Mock
    private LearningBehaviorService learningBehaviorService;

    private LearningBehaviorRecordController controller;

    @BeforeEach
    void setUp() {
        controller = new LearningBehaviorRecordController(learningBehaviorService);
    }

    @Test
    void recordBehaviorShouldRejectDirectFinish() {
        LearningBehaviorRecordDTO request = request(10L, LearnBehaviorType.FINISH, null, null);
        Result<?> result = controller.recordBehavior(request);

        assertEquals(400, result.getCode());
        assertEquals("FINISH 行为由学习进度自动生成，不能直接提交", result.getMsg());
        verify(learningBehaviorService, never()).recordBehavior(10L, LearnBehaviorType.FINISH, null, null);
    }

    @Test
    void recordBehaviorShouldDelegateSupportedBehavior() {
        LearningBehaviorRecordDTO request = request(10L, LearnBehaviorType.STUDY, 60, "study-event-1");
        when(learningBehaviorService.recordBehavior(
                10L, LearnBehaviorType.STUDY, 60, "study-event-1"))
                .thenReturn(BehaviorRecordOutcome.PROCESSED);
        Result<?> result = controller.recordBehavior(request);

        assertEquals(200, result.getCode());
        assertEquals(false, ((LearningBehaviorRecordVO) result.getData()).replayed());
        verify(learningBehaviorService).recordBehavior(
                10L, LearnBehaviorType.STUDY, 60, "study-event-1");
    }

    @Test
    void recordBehaviorShouldExposeIdempotentReplay() {
        LearningBehaviorRecordDTO request = request(10L, LearnBehaviorType.STUDY, 60, "study-event-1");
        when(learningBehaviorService.recordBehavior(
                10L, LearnBehaviorType.STUDY, 60, "study-event-1"))
                .thenReturn(BehaviorRecordOutcome.REPLAYED);

        Result<?> result = controller.recordBehavior(request);

        assertEquals(200, result.getCode());
        assertEquals(true, ((LearningBehaviorRecordVO) result.getData()).replayed());
    }

    @Test
    void recordBehaviorShouldExposeEventIdConflict() {
        LearningBehaviorRecordDTO request = request(10L, LearnBehaviorType.STUDY, 60, "study-event-1");
        when(learningBehaviorService.recordBehavior(
                10L, LearnBehaviorType.STUDY, 60, "study-event-1"))
                .thenThrow(new LearningBehaviorEventConflictException("eventId 已被其他学习事件使用"));

        Result<?> result = controller.recordBehavior(request);

        assertEquals(409, result.getCode());
        assertEquals("eventId 已被其他学习事件使用", result.getMsg());
    }

    private LearningBehaviorRecordDTO request(Long courseId, LearnBehaviorType behaviorType, Integer duration,
            String eventId) {
        LearningBehaviorRecordDTO request = new LearningBehaviorRecordDTO();
        request.setEventId(eventId);
        request.setCourseId(courseId);
        request.setBehaviorType(behaviorType);
        request.setDuration(duration);
        return request;
    }
}
