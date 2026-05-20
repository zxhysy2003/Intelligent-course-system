package com.sy.course_system.agent;

import java.util.List;

import com.sy.course_system.vo.agent.AgentSourceVO;

public class AgentContextSnapshot {
    private final String systemPrompt;
    private final String fallbackSummary;
    private final List<AgentSourceVO> sources;

    public AgentContextSnapshot(String systemPrompt, String fallbackSummary, List<AgentSourceVO> sources) {
        this.systemPrompt = systemPrompt;
        this.fallbackSummary = fallbackSummary;
        this.sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getFallbackSummary() {
        return fallbackSummary;
    }

    public List<AgentSourceVO> getSources() {
        return sources;
    }
}
