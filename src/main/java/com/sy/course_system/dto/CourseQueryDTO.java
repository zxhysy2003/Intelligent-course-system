package com.sy.course_system.dto;

import java.util.List;

import com.sy.course_system.enums.CourseOrderType;

public class CourseQueryDTO {
    // 分页参数
    private Integer page = 1;
    private Integer pageSize = 10;

    // 搜索 & 过滤条件
    // 查询参数
    private String keyword;
    // 分类ID
    private Long categoryId;
    // 难度
    private Integer difficulty;

    // 候选课程集合(CF / Neo4j / 热门课程)
    private List<Long> courseIds;
    // 排序策略
    private CourseOrderType orderBy;

    // TODO: CF 的 score 字段


    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public List<Long> getCourseIds() {
        return courseIds;
    }

    public void setCourseIds(List<Long> courseIds) {
        this.courseIds = courseIds;
    }

    public CourseOrderType getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(CourseOrderType orderBy) {
        this.orderBy = orderBy;
    }

    
}
