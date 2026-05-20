package com.sy.course_system.vo.agent;

import java.util.List;

public class AgentChatResponseVO {
    private Long sessionId;
    private AgentMessageVO userMessage;
    private AgentMessageVO assistantMessage;
    private List<AgentSourceVO> sources;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public AgentMessageVO getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(AgentMessageVO userMessage) {
        this.userMessage = userMessage;
    }

    public AgentMessageVO getAssistantMessage() {
        return assistantMessage;
    }

    public void setAssistantMessage(AgentMessageVO assistantMessage) {
        this.assistantMessage = assistantMessage;
    }

    public List<AgentSourceVO> getSources() {
        return sources;
    }

    public void setSources(List<AgentSourceVO> sources) {
        this.sources = sources;
    }
}
