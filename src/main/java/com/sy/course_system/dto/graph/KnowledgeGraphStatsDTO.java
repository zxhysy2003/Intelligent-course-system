package com.sy.course_system.dto.graph;

public class KnowledgeGraphStatsDTO {
    private Integer nodeCount;
    private Integer edgeCount;
    private Double avgMastery;

    public KnowledgeGraphStatsDTO() {}

    public KnowledgeGraphStatsDTO(Integer nodeCount, Integer edgeCount, Double avgMastery) {
        this.nodeCount = nodeCount;
        this.edgeCount = edgeCount;
        this.avgMastery = avgMastery;
    }

    public Integer getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(Integer nodeCount) {
        this.nodeCount = nodeCount;
    }

    public Integer getEdgeCount() {
        return edgeCount;
    }

    public void setEdgeCount(Integer edgeCount) {
        this.edgeCount = edgeCount;
    }

    public Double getAvgMastery() {
        return avgMastery;
    }

    public void setAvgMastery(Double avgMastery) {
        this.avgMastery = avgMastery;
    }
}
