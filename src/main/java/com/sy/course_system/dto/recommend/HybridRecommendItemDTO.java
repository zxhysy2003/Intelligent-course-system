package com.sy.course_system.dto.recommend;

import java.util.List;

/**
 * 混合推荐返回项。
 *
 * 该 DTO 同时服务于：
 * 1) 常规 CF+KG 推荐；
 * 2) 用户冷启动推荐；
 * 3) 新课冷启动补充推荐。
 *
 * 通过 `recommendSource` 与 `isNewCourse` 区分来源，确保前端与埋点可识别推荐路径。
 */
public class HybridRecommendItemDTO {
    private Long courseId;
    private String title;
    private String coverUrl;
    private Integer difficulty;
    private Double cfScore; // 协同过滤分数
    private Double readiness; // 课程准备度
    private Double finalScore; // 融合后分数
    private String reason;
    // 推荐来源：如 CF / COLD_START_USER / COLD_START_COURSE
    private String recommendSource;
    // 是否由“新课冷启动”策略注入
    private Boolean isNewCourse;

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

    public String getRecommendSource() {
        return recommendSource;
    }

    public void setRecommendSource(String recommendSource) {
        this.recommendSource = recommendSource;
    }

    public Boolean getIsNewCourse() {
        return isNewCourse;
    }

    public void setIsNewCourse(Boolean isNewCourse) {
        this.isNewCourse = isNewCourse;
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
