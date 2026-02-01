package com.sy.course_system.dto.recommend;

import java.time.LocalDateTime;

public class UserCourseBaseScoreDTO {
    private Long userId;
    private Long courseId;
    private Double baseScore;
    private LocalDateTime lastTime;
    
    public Long getUserId() {
        return userId;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    public Long getCourseId() {
        return courseId;
    }
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }
    public Double getBaseScore() {
        return baseScore;
    }
    public void setBaseScore(Double baseScore) {
        this.baseScore = baseScore;
    }
    public LocalDateTime getLastTime() {
        return lastTime;
    }
    public void setLastTime(LocalDateTime lastTime) {
        this.lastTime = lastTime;
    }

    
}
