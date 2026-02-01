package com.sy.course_system.dto.recommend;

import java.util.List;

public class HybridRecommendItemDTO {
    private Long courseId;
    private Double cfScore; // 协同过滤分数
    private Double finalScore; // 融合后分数

    // 图谱解释： 课程知识点
    private List<KnowledgeBriefDTO> knowledgePoints;
    // 图谱补全： 缺失的先修知识点
    private List<KnowledgeBriefDTO> missingPrerequisites;
    // 图谱路径： 学习路径推荐（多条）
    private List<List<KnowledgeBriefDTO>> learningPaths;

    public HybridRecommendItemDTO() {
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Double getCfScore() {
        return cfScore;
    }

    public void setCfScore(Double cfScore) {
        this.cfScore = cfScore;
    }

    public Double getFinalScore() {
        return finalScore;
    }

    public void setFinalScore(Double finalScore) {
        this.finalScore = finalScore;
    }

    public List<KnowledgeBriefDTO> getKnowledgePoints() {
        return knowledgePoints;
    }

    public void setKnowledgePoints(List<KnowledgeBriefDTO> knowledgePoints) {
        this.knowledgePoints = knowledgePoints;
    }

    public List<KnowledgeBriefDTO> getMissingPrerequisites() {
        return missingPrerequisites;
    }

    public void setMissingPrerequisites(List<KnowledgeBriefDTO> missingPrerequisites) {
        this.missingPrerequisites = missingPrerequisites;
    }

    public List<List<KnowledgeBriefDTO>> getLearningPaths() {
        return learningPaths;
    }

    public void setLearningPaths(List<List<KnowledgeBriefDTO>> learningPaths) {
        this.learningPaths = learningPaths;
    }

    
}
