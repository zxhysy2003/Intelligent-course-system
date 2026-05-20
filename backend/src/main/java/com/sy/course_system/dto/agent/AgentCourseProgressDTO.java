package com.sy.course_system.dto.agent;

import java.time.LocalDateTime;

public class AgentCourseProgressDTO {
    private Long courseId;
    private String title;
    private Integer difficulty;
    private Integer progress;
    private Integer learnedSeconds;
    private Integer status;
    private Integer favorite;
    private LocalDateTime lastLearnTime;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Integer getLearnedSeconds() {
        return learnedSeconds;
    }

    public void setLearnedSeconds(Integer learnedSeconds) {
        this.learnedSeconds = learnedSeconds;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getFavorite() {
        return favorite;
    }

    public void setFavorite(Integer favorite) {
        this.favorite = favorite;
    }

    public LocalDateTime getLastLearnTime() {
        return lastLearnTime;
    }

    public void setLastLearnTime(LocalDateTime lastLearnTime) {
        this.lastLearnTime = lastLearnTime;
    }
}
