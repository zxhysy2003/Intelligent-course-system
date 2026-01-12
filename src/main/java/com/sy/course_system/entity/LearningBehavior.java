package com.sy.course_system.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sy.course_system.enums.LearnBehaviorType;

@TableName("learning_behavior")
public class LearningBehavior {
    private Long id;
    private Long userId;
    private Long courseId;
    private LearnBehaviorType behaviorType;
    private Integer duration;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

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
    public LearnBehaviorType getBehaviorType() {
        return behaviorType;
    }
    public void setBehaviorType(LearnBehaviorType behaviorType) {
        this.behaviorType = behaviorType;
    }
    public Integer getDuration() {
        return duration;
    }
    public void setDuration(Integer duration) {
        this.duration = duration;
    }
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    
}
