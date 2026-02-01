package com.sy.course_system.dto.course;

import com.sy.course_system.enums.CourseOrderType;

public class CourseQueryDTO {
    // 分页参数
    private Integer page = 1;
    private Integer pageSize = 9;

    // 搜索 & 过滤条件
    // 查询参数
    private String keyword;
    // 分类ID
    private Long categoryId;
    
    private Integer sortBy = CourseOrderType.DEFAULT.getCode(); // 排序方式，默认按人数排序

    private Integer difficulty;

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

    public Integer getSortBy() {
        return sortBy;
    }

    public void setSortBy(Integer sortBy) {
        this.sortBy = sortBy;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }
}