package com.sy.course_system.service;

import java.util.List;

import com.sy.course_system.dto.AbilityRadarDTO;
import com.sy.course_system.dto.ProgressChartDTO;

public interface LearningAnalysisService {

    void increaseCourseHot(Long courseId, double score);

    void removeCourseHot(Long courseId);

    void removeCourseHotBatch(List<Long> courseIds);

    List<Long> getHotCoursesByRange(int startInclusive, int limit);

    void refreshUserRecommendCache(Long userId);

    ProgressChartDTO getProgressChart(Long userId, Integer days);

    AbilityRadarDTO getAbilityRadar(Long userId);
}
