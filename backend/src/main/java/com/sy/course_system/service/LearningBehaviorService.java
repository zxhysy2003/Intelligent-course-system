package com.sy.course_system.service;

import com.sy.course_system.enums.BehaviorRecordOutcome;
import com.sy.course_system.enums.LearnBehaviorType;

public interface LearningBehaviorService {

    BehaviorRecordOutcome recordBehavior(Long courseId, LearnBehaviorType behaviorType, Integer duration,
            String eventId);

}
