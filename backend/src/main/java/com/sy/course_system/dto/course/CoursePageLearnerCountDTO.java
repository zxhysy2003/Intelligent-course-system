package com.sy.course_system.dto.course;

public class CoursePageLearnerCountDTO {
    private Long courseId;
    private Integer learners;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Integer getLearners() {
        return learners;
    }

    public void setLearners(Integer learners) {
        this.learners = learners;
    }
}
