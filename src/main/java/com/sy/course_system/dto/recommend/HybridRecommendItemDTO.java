package com.sy.course_system.dto.recommend;

import java.util.List;

import com.sy.course_system.vo.KnowledgeMasteryVO;
import com.sy.course_system.vo.KnowledgeVO;

/**
 * 混合推荐返回项。
 *
 * 该 DTO 同时服务于：
 * 1) 常规 CF+KG 推荐；
 * 2) 用户冷启动推荐；
 * 3) 新课冷启动补充推荐。
 *
 * 通过 `recommendSource` 与 `isNewCourse` 区分来源，确保前端与埋点可识别推荐路径。
 *
 * 字段语义上需要特别区分两类“分数”：
 * 1) finalScore：后端内部排序分，只保证在“同一条推荐链路内部”可比较，
 *    例如 CF 主链路里的 finalScore 可以互相比较，但不能直接拿去和冷启动原始分做横向解释。
 * 2) recommendScore：最终统一给前端展示的推荐分，固定压到 60~95 区间，
 *    目的是让不同来源的推荐结果都能给用户一个稳定、可读的“推荐度”。
 *
 * 这两个字段都保留：
 * - finalScore 继续服务排序与调试；
 * - recommendScore 专门服务推荐页展示，后续前端应优先使用它。
 */
public class HybridRecommendItemDTO {
    private Long courseId;
    private String title;
    // 保留兼容字段：当前推荐页虽然暂时不用，但它仍可能被其他页面或旧调用方依赖，
    // 因此本次只允许“前端忽略”，不直接从接口删除，避免造成兼容性破坏。
    private String coverUrl;
    private Integer difficulty;
    // 协同过滤原始分，仅在 CF 主链路下有明确意义。
    // 冷启动、新课兜底、热门兜底等场景可能为 null。
    private Double cfScore;
    // 图谱可学习性（readiness），用于表达“当前是否适合直接学这门课”。
    // 部分场景下可能是图谱真实值，也可能因缺图谱数据走排序兜底值，前端解释时应配合 reason 使用。
    private Double readiness;
    // 后端内部排序分，不保证不同推荐来源之间同量纲：
    // - CF 主链路里通常是 0~1 的融合分；
    // - 用户冷启动里可能是启发式原始分；
    // - 热门兜底里当前固定为 0.0，仅表示它不参与该链路排序解释。
    //
    // 因此它适合后端排序/排障，不适合直接展示给用户。
    private Double finalScore;
    // 统一后的前端展示分，固定映射到 60~95 区间。
    // 它不反向驱动排序，只是把不同来源的结果统一翻译成一个稳定的展示口径。
    private Integer recommendScore;
    private String reason;
    // 推荐来源：如 CF / COLD_START_USER / COLD_START_COURSE / HOT_FALLBACK。
    // 展示分的归一化规则会按来源分支处理，因此这里不仅是埋点字段，也影响 recommendScore 的换算逻辑。
    private String recommendSource;
    // 是否由“新课冷启动”策略注入。
    // 注意它描述的是“曝光策略来源”，不等价于 recommendSource；
    // 例如新课插入通常会同时表现为 recommendSource=COLD_START_COURSE 且 isNewCourse=true。
    private Boolean isNewCourse;

    // 图谱解释：课程知识点概览，用于回答“这门课讲什么”。
    private List<KnowledgeVO> knowledgePoints;
    // 图谱补全：缺失的先修知识点及掌握度，用于回答“还差什么才能学”。
    private List<KnowledgeMasteryVO> missingPrerequisitesMastery;
    // 图谱路径：学习路径推荐（多条），用于回答“应该先补哪些内容”。
    private List<List<KnowledgeVO>> learningPaths;

    public HybridRecommendItemDTO() {
    }

    public HybridRecommendItemDTO(Long courseId, String title, Double cfScore, Double readiness, Double finalScore,
                                  List<KnowledgeVO> knowledgePoints,
                                  List<KnowledgeMasteryVO> missingPrerequisitesMastery,
                                  List<List<KnowledgeVO>> learningPaths) {
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

    public Integer getRecommendScore() {
        return recommendScore;
    }

    public void setRecommendScore(Integer recommendScore) {
        this.recommendScore = recommendScore;
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
