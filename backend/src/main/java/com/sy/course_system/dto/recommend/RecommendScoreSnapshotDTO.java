package com.sy.course_system.dto.recommend;

import java.time.LocalDateTime;

public class RecommendScoreSnapshotDTO {
    private Long userId;
    private Long courseId;
    private Double rawScore;
    private Double score;
    private LocalDateTime lastBehaviorTime;

    public RecommendScoreSnapshotDTO() {
    }

    public RecommendScoreSnapshotDTO(Long userId, Long courseId, Double rawScore, Double score,
            LocalDateTime lastBehaviorTime) {
        this.userId = userId;
        this.courseId = courseId;
        this.rawScore = rawScore;
        this.score = score;
        this.lastBehaviorTime = lastBehaviorTime;
    }

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

    public Double getRawScore() {
        return rawScore;
    }

    public void setRawScore(Double rawScore) {
        this.rawScore = rawScore;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public LocalDateTime getLastBehaviorTime() {
        return lastBehaviorTime;
    }

    public void setLastBehaviorTime(LocalDateTime lastBehaviorTime) {
        this.lastBehaviorTime = lastBehaviorTime;
    }
}
