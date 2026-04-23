package com.sy.course_system.dto.recommend;

/**
 * 新课标签明细行（第二阶段按 courseIds 回补）。
 */
public class NewCourseTagRowDTO {

    private Long courseId;
    private Long tagId;
    private String tagName;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }
}
