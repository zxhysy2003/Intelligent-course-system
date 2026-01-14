package com.sy.course_system.vo;

import java.util.List;

// TODO: 完善 CourseVO 字段
public class CourseVO {
     // ===== 基础信息 =====
    private Long id;
    private String title;
    private String description;
    private String coverUrl;
    private Integer difficulty;
    private Integer duration;
    private List<String> tags;

    // ===== 推荐 & 排序相关 =====
    private Double score;        // 推荐评分 / 协同过滤预测值
    private Double avgRating;    // 全站平均评分（可选）
    private Long viewCount;      // 热度（可从 Redis 或表中）

    // ===== 用户学习状态 =====
    private Boolean learned;     // 是否学过
    private Boolean finished;    // 是否学完
    private Integer progress;    // 0-100 学习进度

    // ===== 学习路径相关 =====
    private Boolean isInPath;    // 是否在推荐学习路径中

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

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Double getAvgRating() {
        return avgRating;
    }

    public void setAvgRating(Double avgRating) {
        this.avgRating = avgRating;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public Boolean getLearned() {
        return learned;
    }

    public void setLearned(Boolean learned) {
        this.learned = learned;
    }

    public Boolean getFinished() {
        return finished;
    }

    public void setFinished(Boolean finished) {
        this.finished = finished;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Boolean getIsInPath() {
        return isInPath;
    }

    public void setIsInPath(Boolean isInPath) {
        this.isInPath = isInPath;
    }

    
}
