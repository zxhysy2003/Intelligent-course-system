package com.sy.course_system.dto.course;

public class CourseHotScoreDTO {
    private Long courseId;
    private Double hotScore;

    public CourseHotScoreDTO() {
    }

    public CourseHotScoreDTO(Long courseId, Double hotScore) {
        this.courseId = courseId;
        this.hotScore = hotScore;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Double getHotScore() {
        return hotScore;
    }

    public void setHotScore(Double hotScore) {
        this.hotScore = hotScore;
    }
}
