package com.sy.course_system.dto.graph;

public class KnowledgeGraphNodeDTO {
    private Long kpId;
    private String id;
    private String name;
    private Integer difficulty;
    private Double mastery;
    private Boolean inCourse;
    private Integer depth;

    public KnowledgeGraphNodeDTO() {}

    public Long getKpId() {
        return kpId;
    }

    public void setKpId(Long kpId) {
        this.kpId = kpId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public Double getMastery() {
        return mastery;
    }

    public void setMastery(Double mastery) {
        this.mastery = mastery;
    }

    public Boolean getInCourse() {
        return inCourse;
    }

    public void setInCourse(Boolean inCourse) {
        this.inCourse = inCourse;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }
}
