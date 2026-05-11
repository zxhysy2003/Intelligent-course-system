package com.sy.course_system.service;

public interface RecommendScoreSnapshotService {

    void refreshUserCourseScore(Long userId, Long courseId);

    void rebuildAllScores();
}
