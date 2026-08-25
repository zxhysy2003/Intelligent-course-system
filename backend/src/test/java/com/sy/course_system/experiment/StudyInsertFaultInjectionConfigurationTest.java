package com.sy.course_system.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;

import com.sy.course_system.entity.LearningBehavior;
import com.sy.course_system.mapper.LearningBehaviorMapper;

class StudyInsertFaultInjectionConfigurationTest {

    @Test
    void shouldInjectOnlyOnceAfterTargetEventWasInserted() {
        LearningBehaviorMapper target = mock(LearningBehaviorMapper.class);
        LearningBehavior behavior = behavior("con03-rollback-event");
        when(target.insertStudyIfAbsent(behavior)).thenReturn(1);
        LearningBehaviorMapper proxy = wrap(target, true, "con03-rollback-event");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> proxy.insertStudyIfAbsent(behavior));
        int retryResult = proxy.insertStudyIfAbsent(behavior);

        assertEquals(StudyInsertFaultInjectionConfiguration.FAILURE_MESSAGE, failure.getMessage());
        assertEquals(1, retryResult);
        verify(target, times(2)).insertStudyIfAbsent(behavior);
    }

    @Test
    void shouldIgnoreDifferentEventOrDisabledFault() {
        LearningBehaviorMapper target = mock(LearningBehaviorMapper.class);
        LearningBehavior differentEvent = behavior("con03-another-event");
        LearningBehavior targetEvent = behavior("con03-rollback-event");
        when(target.insertStudyIfAbsent(differentEvent)).thenReturn(1);
        when(target.insertStudyIfAbsent(targetEvent)).thenReturn(1);

        LearningBehaviorMapper enabledProxy = wrap(target, true, "con03-rollback-event");
        LearningBehaviorMapper disabledProxy = wrap(target, false, "con03-rollback-event");

        assertEquals(1, enabledProxy.insertStudyIfAbsent(differentEvent));
        assertEquals(1, disabledProxy.insertStudyIfAbsent(targetEvent));
    }

    private LearningBehaviorMapper wrap(
            LearningBehaviorMapper target,
            boolean enabled,
            String targetEventId) {
        BeanPostProcessor processor = StudyInsertFaultInjectionConfiguration
                .studyInsertFaultInjectionBeanPostProcessor(enabled, targetEventId);
        return assertInstanceOf(LearningBehaviorMapper.class,
                processor.postProcessAfterInitialization(target, "learningBehaviorMapper"));
    }

    private LearningBehavior behavior(String eventId) {
        LearningBehavior behavior = new LearningBehavior();
        behavior.setEventId(eventId);
        return behavior;
    }
}
