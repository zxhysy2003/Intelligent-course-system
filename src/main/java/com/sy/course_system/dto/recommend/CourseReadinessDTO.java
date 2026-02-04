package com.sy.course_system.dto.recommend;

import java.util.List;

public class CourseReadinessDTO {
    private Long courseId;
    private Double readiness; // 0~1
    private List<KnowledgeMasteryDTO> missing; // 缺失的先修知识点列表
    
    public Long getCourseId() {
        return courseId;
    }
    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }
    public Double getReadiness() {
        return readiness;
    }
    public void setReadiness(Double readiness) {
        this.readiness = readiness;
    }
    public List<KnowledgeMasteryDTO> getMissing() {
        return missing;
    }
    public void setMissing(List<KnowledgeMasteryDTO> missing) {
        this.missing = missing;
    }
    
    
}
