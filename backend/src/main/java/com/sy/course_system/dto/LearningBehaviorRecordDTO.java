package com.sy.course_system.dto;

import com.sy.course_system.enums.LearnBehaviorType;

public class LearningBehaviorRecordDTO {
    private Long courseId;
    private LearnBehaviorType behaviorType;
    private Integer duration;

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
}
