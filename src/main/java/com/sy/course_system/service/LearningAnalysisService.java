package com.sy.course_system.service;

import java.util.List;
import com.sy.course_system.vo.CourseRecommendVO;

public interface LearningAnalysisService {
    
    Integer getMyTotalStudyTime();

    List<Long> getMyLearnedCourses();

    void increaseCourseHot(Long courseId, double score);

    List<CourseRecommendVO> getHotCourses(Integer topN);

    void refreshUserRecommendCache(Long userId);
}
