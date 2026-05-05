package com.sy.course_system.dto;

public class ProgressDailyPointDTO {
    private String day;
    private Integer studySeconds;
    private Integer activeCourses;

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public Integer getStudySeconds() {
        return studySeconds;
    }

    public void setStudySeconds(Integer studySeconds) {
        this.studySeconds = studySeconds;
    }

    public Integer getActiveCourses() {
        return activeCourses;
    }

    public void setActiveCourses(Integer activeCourses) {
        this.activeCourses = activeCourses;
    }
}
