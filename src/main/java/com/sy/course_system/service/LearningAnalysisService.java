package com.sy.course_system.service;

import java.util.List;

import com.sy.course_system.vo.CourseVO;

public interface LearningAnalysisService {
    
    Integer getMyTotalStudyTime();

    List<Long> getMyLearnedCourses();

    void increaseCourseHot(Long courseId, double score);

    List<Long> getHotCourses(Integer topN);

    void refreshUserRecommendCache(Long userId);

    List<CourseVO> sortCoursesByHotness(List<CourseVO> courses);
}
