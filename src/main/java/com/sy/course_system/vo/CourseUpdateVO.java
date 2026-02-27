package com.sy.course_system.vo;

import com.sy.course_system.dto.course.CourseRegisterOptionsDTO;

public class CourseUpdateVO {
    private Long id;
    private String title;
    private String description;
    private String coverUrl;
    private Integer difficulty;
    private Integer duration; // 课程时长，单位：秒数
    private Integer categoryId;
    private CourseRegisterOptionsDTO options; // 注册选项，该课程已经包含标签和知识点列表
    
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
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
    public CourseRegisterOptionsDTO getOptions() {
        return options;
    }
    public void setOptions(CourseRegisterOptionsDTO options) {
        this.options = options;
    }

    
}
