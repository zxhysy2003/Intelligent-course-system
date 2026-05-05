package com.sy.course_system.dto;

import java.util.List;

public class ProgressChartDTO {
    private List<String> dates; // xAxis.data
    private List<Integer> studySeconds; // series[0].data
    private List<Integer> activeCourses; // series[1].data
    private Integer totalCourses; 
    private Integer finishedCourses;
    private Double avgProgress;
    private Integer totalLearnedSeconds;
    
    public List<String> getDates() {
        return dates;
    }
    public void setDates(List<String> dates) {
        this.dates = dates;
    }
    public List<Integer> getStudySeconds() {
        return studySeconds;
    }
    public void setStudySeconds(List<Integer> studySeconds) {
        this.studySeconds = studySeconds;
    }
    public List<Integer> getActiveCourses() {
        return activeCourses;
    }
    public void setActiveCourses(List<Integer> activeCourses) {
        this.activeCourses = activeCourses;
    }
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
