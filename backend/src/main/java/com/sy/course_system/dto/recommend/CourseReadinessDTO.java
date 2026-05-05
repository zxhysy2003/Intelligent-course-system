package com.sy.course_system.dto.recommend;

import java.util.List;

import com.sy.course_system.vo.KnowledgeMasteryVO;

public class CourseReadinessDTO {
    private Long courseId;
    private Double readiness; // 0~1
    private List<KnowledgeMasteryVO> missing; // 缺失的先修知识点列表
    
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
    public List<KnowledgeMasteryVO> getMissing() {
        return missing;
    }
    public void setMissing(List<KnowledgeMasteryVO> missing) {
        this.missing = missing;
    }
    
    
}
