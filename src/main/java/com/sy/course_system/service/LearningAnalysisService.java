package com.sy.course_system.service;

import java.util.List;

import com.sy.course_system.dto.AbilityRadarDTO;
import com.sy.course_system.dto.ProgressChartDTO;
import com.sy.course_system.vo.CourseVO;

public interface LearningAnalysisService {

    void increaseCourseHot(Long courseId, double score);

    List<Long> getHotCourses(Integer topN);

    void refreshUserRecommendCache(Long userId);

    List<CourseVO> sortCoursesByHotness(List<CourseVO> courses);

    ProgressChartDTO getProgressChart(Long userId, Integer days);

    AbilityRadarDTO getAbilityRadar(Long userId);
}
