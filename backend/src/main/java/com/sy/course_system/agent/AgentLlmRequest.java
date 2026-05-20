package com.sy.course_system.agent;

import java.util.List;

public class AgentLlmRequest {
    private String systemPrompt;
    private String fallbackSummary;
    private List<AgentLlmMessage> messages;

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getFallbackSummary() {
        return fallbackSummary;
    }

    public void setFallbackSummary(String fallbackSummary) {
        this.fallbackSummary = fallbackSummary;
    }

    public List<AgentLlmMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<AgentLlmMessage> messages) {
        this.messages = messages;
    }
}
