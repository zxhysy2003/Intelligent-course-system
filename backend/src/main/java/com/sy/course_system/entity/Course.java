package com.sy.course_system.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 课程实体。
 *
 * 备注：
 * - publishTime 表示“首次上线时间”，用于新课冷启动时间窗判断；
 * - 该字段一旦写入，后续上下线切换不应反复覆盖，避免破坏“新课”语义。
 */
@TableName("course")
public class Course {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    private String coverUrl;
    private Integer difficulty;
    private Integer duration; // 课程时长，单位：秒数
    private Integer status; // 0-DRAFT 1-ONLINE 2-OFFLINE
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    // 首次从 DRAFT/OFFLINE 转为 ONLINE 的时间，不等同于最近更新时间
    private LocalDateTime publishTime; // 课程首次上线时间
    
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
    public Integer getStatus() {
        return status;
    }
    public void setStatus(Integer status) {
        this.status = status;
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
    public LocalDateTime getPublishTime() {
        return publishTime;
    }
    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }
    
}
