package com.sy.course_system.dto.course;

import java.util.List;

public class CourseRegisterDTO {
    private String title;
    private String description;
    private String coverUrl;
    private Integer difficulty;
    private Integer duration; // 课程时长，单位：秒数
    private Integer categoryId; // 课程分类ID
    private List<Integer> tagIds; // 课程标签ID列表
    private List<Long> knowledgePointIds; // 知识点ID列表
    
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
    public Integer getCategoryId() {
        return categoryId;
    }
    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }
    public List<Integer> getTagIds() {
        return tagIds;
    }
    public void setTagIds(List<Integer> tagIds) {
        this.tagIds = tagIds;
    }
    public List<Long> getKnowledgePointIds() {
        return knowledgePointIds;
    }
    public void setKnowledgePointIds(List<Long> knowledgePointIds) {
        this.knowledgePointIds = knowledgePointIds;
    }
    
}
