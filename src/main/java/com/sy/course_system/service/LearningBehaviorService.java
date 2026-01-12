package com.sy.course_system.service;

import java.util.List;

import com.sy.course_system.dto.UserCourseScoreDTO;
import com.sy.course_system.entity.LearningBehavior;
import com.sy.course_system.enums.LearnBehaviorType;

public interface LearningBehaviorService {

    void recordBehavior(Long courseId, LearnBehaviorType behaviorType, Integer duration);
    
    List<LearningBehavior> listAllBehaviors();

    List<UserCourseScoreDTO> listAggregatedScores();

    void startStudy(Long courseId);

    void endStudy(Long courseId);
}
