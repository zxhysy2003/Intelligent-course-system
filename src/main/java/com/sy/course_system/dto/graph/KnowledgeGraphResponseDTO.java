package com.sy.course_system.dto.graph;

import java.util.List;

public class KnowledgeGraphResponseDTO {
    private Long courseId;
    private String title;
    private Long userId;
    private List<KnowledgeGraphNodeDTO> nodes;
    private List<KnowledgeGraphLinkDTO> links;
    private KnowledgeGraphStatsDTO stats;

    public KnowledgeGraphResponseDTO() {}

    public KnowledgeGraphResponseDTO(Long courseId, String title, Long userId, List<KnowledgeGraphNodeDTO> nodes,
            List<KnowledgeGraphLinkDTO> links, KnowledgeGraphStatsDTO stats) {
        this.courseId = courseId;
        this.title = title;
        this.userId = userId;
        this.nodes = nodes;
        this.links = links;
        this.stats = stats;
    }

    public Long getCourseId() {
        return courseId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public List<KnowledgeGraphNodeDTO> getNodes() {
        return nodes;
    }

    public void setNodes(List<KnowledgeGraphNodeDTO> nodes) {
        this.nodes = nodes;
    }

    public List<KnowledgeGraphLinkDTO> getLinks() {
        return links;
    }

    public void setLinks(List<KnowledgeGraphLinkDTO> links) {
        this.links = links;
    }

    public KnowledgeGraphStatsDTO getStats() {
        return stats;
    }

    public void setStats(KnowledgeGraphStatsDTO stats) {
        this.stats = stats;
    }
}
