package com.sy.course_system.dto;

public class RecommendItemDTO {
    private Long courseId;
    private Double score;
    
    public Long getCourseId() {
        return courseId;
    }
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }
    public Double getScore() {
        return score;
    }
    public void setScore(Double score) {
        this.score = score;
    }

    
}
