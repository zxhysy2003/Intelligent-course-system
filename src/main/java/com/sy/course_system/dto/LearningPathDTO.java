package com.sy.course_system.dto;

public class LearningPathDTO {
    private Long fromId;
    private String fromName;
    private Long toId;
    private String toName;
    private Long support;

    public LearningPathDTO(Long fromId, String fromName, Long toId, String toName, Long support) {
        this.fromId = fromId;
        this.fromName = fromName;
        this.toId = toId;
        this.toName = toName;
        this.support = support;
    }

    public LearningPathDTO() {
    }

    public Long getFromId() {
        return fromId;
    }

    public void setFromId(Long fromId) {
        this.fromId = fromId;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public Long getToId() {
        return toId;
    }

    public void setToId(Long toId) {
        this.toId = toId;
    }

    public String getToName() {
        return toName;
    }

    public void setToName(String toName) {
        this.toName = toName;
    }

    public Long getSupport() {
        return support;
    }

    public void setSupport(Long support) {
        this.support = support;
    }

    
}
