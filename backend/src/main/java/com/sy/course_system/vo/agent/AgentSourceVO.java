package com.sy.course_system.vo.agent;

public class AgentSourceVO {
    private String type;
    private String title;
    private String summary;
    private String referenceId;

    public AgentSourceVO() {
    }

    public AgentSourceVO(String type, String title, String summary, String referenceId) {
        this.type = type;
        this.title = title;
        this.summary = summary;
        this.referenceId = referenceId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }
}
