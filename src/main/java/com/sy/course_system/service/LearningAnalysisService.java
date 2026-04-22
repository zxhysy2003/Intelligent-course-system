package com.sy.course_system.service;

import java.util.List;

import com.sy.course_system.dto.AbilityRadarDTO;
import com.sy.course_system.dto.ProgressChartDTO;
import com.sy.course_system.vo.CourseVO;

public interface LearningAnalysisService {

    void increaseCourseHot(Long courseId, double score);

    void removeCourseHot(Long courseId);

    void removeCourseHotBatch(List<Long> courseIds);

    List<Long> getHotCourses(Integer topN);

    List<Long> getHotCoursesByRange(int startInclusive, int limit);

    void refreshUserRecommendCache(Long userId);

    List<CourseVO> sortCoursesByHotness(List<CourseVO> courses);

    ProgressChartDTO getProgressChart(Long userId, Integer days);

    AbilityRadarDTO getAbilityRadar(Long userId);
}
