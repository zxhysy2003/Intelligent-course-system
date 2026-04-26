package com.sy.course_system.vo;

import java.util.List;

/**
 * 推荐页正式出参项。
 *
 * 设计原则：
 * 1) 只保留前端展示稳定依赖的字段；
 * 2) 不直接暴露内部排序分、协同过滤分、推荐来源等实现细节；
 * 3) 图谱解释字段继续保留，方便前端展示“为什么推荐、还差什么、建议怎么学”。
 */
public class HybridRecommendItemVO {
    private Long courseId;
    private String title;
    private Integer difficulty;
    private Integer recommendScore;
    private String reason;
    private Double readiness;
    private Boolean isNewCourse;
    private List<KnowledgeVO> knowledgePoints;
    private List<KnowledgeMasteryVO> missingPrerequisitesMastery;
    private List<List<KnowledgeVO>> learningPaths;

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

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public Integer getRecommendScore() {
        return recommendScore;
    }

    public void setRecommendScore(Integer recommendScore) {
        this.recommendScore = recommendScore;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Double getReadiness() {
        return readiness;
    }

    public void setReadiness(Double readiness) {
        this.readiness = readiness;
    }

    public Boolean getIsNewCourse() {
        return isNewCourse;
    }

    public void setIsNewCourse(Boolean isNewCourse) {
        this.isNewCourse = isNewCourse;
    }

    public List<KnowledgeVO> getKnowledgePoints() {
        return knowledgePoints;
    }

    public void setKnowledgePoints(List<KnowledgeVO> knowledgePoints) {
        this.knowledgePoints = knowledgePoints;
    }

    public List<KnowledgeMasteryVO> getMissingPrerequisitesMastery() {
        return missingPrerequisitesMastery;
    }

    public void setMissingPrerequisitesMastery(List<KnowledgeMasteryVO> missingPrerequisitesMastery) {
        this.missingPrerequisitesMastery = missingPrerequisitesMastery;
    }

    public List<List<KnowledgeVO>> getLearningPaths() {
        return learningPaths;
    }

    public void setLearningPaths(List<List<KnowledgeVO>> learningPaths) {
        this.learningPaths = learningPaths;
    }
}
