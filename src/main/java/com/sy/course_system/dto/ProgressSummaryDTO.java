package com.sy.course_system.dto;

public class ProgressSummaryDTO {
    private Integer totalCourses;
    private Integer finishedCourses;
    private Double avgProgress;
    private Integer totalLearnedSeconds;

    public Integer getTotalCourses() {
        return totalCourses;
    }

    public void setTotalCourses(Integer totalCourses) {
        this.totalCourses = totalCourses;
    }

    public Integer getFinishedCourses() {
        return finishedCourses;
    }

    public void setFinishedCourses(Integer finishedCourses) {
        this.finishedCourses = finishedCourses;
    }

    public Double getAvgProgress() {
        return avgProgress;
    }

    public void setAvgProgress(Double avgProgress) {
        this.avgProgress = avgProgress;
    }

    public Integer getTotalLearnedSeconds() {
        return totalLearnedSeconds;
    }

    public void setTotalLearnedSeconds(Integer totalLearnedSeconds) {
        this.totalLearnedSeconds = totalLearnedSeconds;
    }
}
