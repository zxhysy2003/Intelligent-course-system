package com.sy.course_system.dto.recommend;

/**
 * 冷启动判定信号摘要。
 *
 * 仅用于推荐服务内部判定，不对外暴露。
 */
public class ColdStartSignalDTO {

    private Long effectiveBehaviorCount;
    private Long studiedCourseCount;
    private Long totalStudySeconds;
    private Long finishCount;

    public Long getEffectiveBehaviorCount() {
        return effectiveBehaviorCount;
    }

    public void setEffectiveBehaviorCount(Long effectiveBehaviorCount) {
        this.effectiveBehaviorCount = effectiveBehaviorCount;
    }

    public Long getStudiedCourseCount() {
        return studiedCourseCount;
    }

    public void setStudiedCourseCount(Long studiedCourseCount) {
        this.studiedCourseCount = studiedCourseCount;
    }

    public Long getTotalStudySeconds() {
        return totalStudySeconds;
    }

    public void setTotalStudySeconds(Long totalStudySeconds) {
        this.totalStudySeconds = totalStudySeconds;
    }

    public Long getFinishCount() {
        return finishCount;
    }

    public void setFinishCount(Long finishCount) {
        this.finishCount = finishCount;
    }
}
