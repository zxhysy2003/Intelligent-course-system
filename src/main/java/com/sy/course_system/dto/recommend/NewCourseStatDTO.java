package com.sy.course_system.dto.recommend;

/**
 * 新课统计结果通用 DTO。
 *
 * `countValue` 在不同查询中可表示：
 * - 知识点数量；
 * - 学习人数。
 */
public class NewCourseStatDTO {

    private Long courseId;
    private Integer countValue;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Integer getCountValue() {
        return countValue;
    }

    public void setCountValue(Integer countValue) {
        this.countValue = countValue;
    }
}
