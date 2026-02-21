package com.sy.course_system.dto.course;

import java.util.List;

public class CourseDeleteDTO {
    private List<Long> courseIds;

    public List<Long> getCourseIds() {
        return courseIds;
    }

    public void setCourseIds(List<Long> courseIds) {
        this.courseIds = courseIds;
    }
}
