package com.sy.course_system.dto.course;

import java.util.List;

public class CourseRegisterDTO {
    private String title;
    private String description;
    private String coverUrl;
    private Integer difficulty;
    private Integer duration; // 课程时长，单位：分钟
    private List<Integer> categoryIds;
    
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public String getCoverUrl() {
        return coverUrl;
    }
    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }
    public Integer getDifficulty() {
        return difficulty;
    }
    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }
    public Integer getDuration() {
        return duration;
    }
    public void setDuration(Integer duration) {
        this.duration = duration;
    }
    public List<Integer> getCategoryIds() {
        return categoryIds;
    }
    public void setCategoryIds(List<Integer> categoryIds) {
        this.categoryIds = categoryIds;
    }

    
}
