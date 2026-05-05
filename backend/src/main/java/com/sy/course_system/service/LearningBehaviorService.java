package com.sy.course_system.service;

import java.util.List;

import com.sy.course_system.dto.recommend.UserCourseScoreDTO;
import com.sy.course_system.enums.LearnBehaviorType;

public interface LearningBehaviorService {

    void recordBehavior(Long courseId, LearnBehaviorType behaviorType, Integer duration);

    List<UserCourseScoreDTO> listAggregatedScores();

}
