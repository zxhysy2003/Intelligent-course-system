package com.sy.course_system.dto.course;

public class CoursePageTagDTO {
    private Long courseId;
    private String tags;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }
}
