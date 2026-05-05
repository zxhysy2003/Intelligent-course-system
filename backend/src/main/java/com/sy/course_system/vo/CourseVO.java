package com.sy.course_system.vo;

import java.time.LocalDateTime;
import java.util.List;

public class CourseVO {
     // ===== 基础信息 =====
    private Long id;
    private String title;
    private String category;
    private List<String> tagList;
    private String description;
    private String cover;
    private Integer learners;
    private Integer difficulty;
    private Boolean enrolled;
    private Integer progress;
    private LocalDateTime lastTime;
    private Integer status; // 上架状态：0-草稿，1-上线，2-下架

    // ===== 推荐 & 排序相关 =====
    private Double score;        // 推荐评分 / 协同过滤预测值
    private Double hotScore;      // 热度（可从 Redis 或表中）

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getTagList() {
        return tagList;
    }

    public void setTagList(List<String> tagList) {
        this.tagList = tagList;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public Integer getLearners() {
        return learners;
    }

    public void setLearners(Integer learners) {
        this.learners = learners;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public Boolean getEnrolled() {
        return enrolled;
    }

    public void setEnrolled(Boolean enrolled) {
        this.enrolled = enrolled;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public LocalDateTime getLastTime() {
        return lastTime;
    }

    public void setLastTime(LocalDateTime lastTime) {
        this.lastTime = lastTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Double getHotScore() {
        return hotScore;
    }

    public void setHotScore(Double hotScore) {
        this.hotScore = hotScore;
    }
}
