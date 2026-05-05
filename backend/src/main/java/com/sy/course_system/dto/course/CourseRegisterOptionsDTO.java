package com.sy.course_system.dto.course;

import java.util.List;

public class CourseRegisterOptionsDTO {
    private List<TagOptionDTO> tags;
    private List<KnowledgePointOptionDTO> knowledgePoints;

    public List<TagOptionDTO> getTags() {
        return tags;
    }

    public void setTags(List<TagOptionDTO> tags) {
        this.tags = tags;
    }

    public List<KnowledgePointOptionDTO> getKnowledgePoints() {
        return knowledgePoints;
    }

    public void setKnowledgePoints(List<KnowledgePointOptionDTO> knowledgePoints) {
        this.knowledgePoints = knowledgePoints;
    }
}
