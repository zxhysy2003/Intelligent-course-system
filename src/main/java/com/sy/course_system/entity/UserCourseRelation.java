package com.sy.course_system.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_course_relation")
public class UserCourseRelation {
    private Long id;
    private Long userId;
    private Long courseId;
    private Integer progress;
    private Integer learnedSeconds; // 已学习时长，单位：秒
    private Integer status; // 0: not started, 1: in progress, 2: completed
    private LocalDateTime lastLearnTime;
    private LocalDateTime completeTime;
    private Integer isFavorite; // 0: no, 1: yes
    private Integer progressSeconds; // 已学习时长，单位：秒
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // Getters and Setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Long getUserId() {
        return userId;
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    public Long getCourseId() {
        return courseId;
    }
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }
    public Integer getProgress() {
        return progress;
    }
    public void setProgress(Integer progress) {
        this.progress = progress;
    }
    public Integer getLearnedSeconds() {
        return learnedSeconds;
    }
    public void setLearnedSeconds(Integer learnedSeconds) {
        this.learnedSeconds = learnedSeconds;
    }
    public Integer getStatus() {
        return status;
    }
    public void setStatus(Integer status) {
        this.status = status;
    }
    public LocalDateTime getLastLearnTime() {
        return lastLearnTime;
    }
    public void setLastLearnTime(LocalDateTime lastLearnTime) {
        this.lastLearnTime = lastLearnTime;
    }
    public LocalDateTime getCompleteTime() {
        return completeTime;
    }
    public void setCompleteTime(LocalDateTime completeTime) {
        this.completeTime = completeTime;
    }
    public Integer getIsFavorite() {
        return isFavorite;
    }
    public void setIsFavorite(Integer isFavorite) {
        this.isFavorite = isFavorite;
    }
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
    public Integer getProgressSeconds() {
        return progressSeconds;
    }
    public void setProgressSeconds(Integer progressSeconds) {
        this.progressSeconds = progressSeconds;
    }
    
    
        
}
