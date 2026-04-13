package com.sy.course_system.dto.recommend;

import java.util.List;

public class HybridRecommendItemDTO {
    private Long courseId;
    private String title;
    private String coverUrl;
    private Integer difficulty;
    private Double cfScore; // 协同过滤分数
    private Double readiness; // 课程准备度
    private Double finalScore; // 融合后分数
    private String reason;

    // 图谱解释： 课程知识点
    private List<KnowledgeBriefDTO> knowledgePoints;
    // 图谱补全： 缺失的先修知识点
    private List<KnowledgeMasteryDTO> missingPrerequisitesMastery;
    // 图谱路径： 学习路径推荐（多条）
    private List<List<KnowledgeBriefDTO>> learningPaths;

    public HybridRecommendItemDTO() {
    }

    public HybridRecommendItemDTO(Long courseId, String title, Double cfScore, Double readiness, Double finalScore,
                                  List<KnowledgeBriefDTO> knowledgePoints,
                                  List<KnowledgeMasteryDTO> missingPrerequisitesMastery,
                                  List<List<KnowledgeBriefDTO>> learningPaths) {
        this.courseId = courseId;
        this.title = title;
        this.cfScore = cfScore;
        this.readiness = readiness;
        this.finalScore = finalScore;
        this.knowledgePoints = knowledgePoints;
        this.missingPrerequisitesMastery = missingPrerequisitesMastery;
        this.learningPaths = learningPaths;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public Double getReadiness() {
        return readiness;
    }

    public void setReadiness(Double readiness) {
        this.readiness = readiness;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<KnowledgeBriefDTO> getKnowledgePoints() {
        return knowledgePoints;
    }

    public void setKnowledgePoints(List<KnowledgeBriefDTO> knowledgePoints) {
        this.knowledgePoints = knowledgePoints;
    }

    public List<KnowledgeMasteryDTO> getMissingPrerequisitesMastery() {
        return missingPrerequisitesMastery;
    }

    public void setMissingPrerequisitesMastery(List<KnowledgeMasteryDTO> missingPrerequisitesMastery) {
        this.missingPrerequisitesMastery = missingPrerequisitesMastery;
    }

    public List<List<KnowledgeBriefDTO>> getLearningPaths() {
        return learningPaths;
    }

    public void setLearningPaths(List<List<KnowledgeBriefDTO>> learningPaths) {
        this.learningPaths = learningPaths;
    }

    
}
