package com.sy.course_system.service;

import com.sy.course_system.enums.LearnBehaviorType;

public interface LearningBehaviorService {

    void recordBehavior(Long courseId, LearnBehaviorType behaviorType, Integer duration);

}
