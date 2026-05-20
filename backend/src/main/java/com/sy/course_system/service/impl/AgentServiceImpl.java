package com.sy.course_system.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.agent.AgentContextAssembler;
import com.sy.course_system.agent.AgentContextSnapshot;
import com.sy.course_system.agent.AgentLlmClient;
import com.sy.course_system.agent.AgentLlmMessage;
import com.sy.course_system.agent.AgentLlmRequest;
import com.sy.course_system.config.AgentProperties;
import com.sy.course_system.converter.AgentMapperStruct;
import com.sy.course_system.dto.agent.AgentChatRequestDTO;
import com.sy.course_system.dto.agent.AgentSessionTitleDTO;
import com.sy.course_system.entity.AgentMessage;
import com.sy.course_system.entity.AgentSession;
import com.sy.course_system.mapper.AgentMessageMapper;
import com.sy.course_system.mapper.AgentSessionMapper;
import com.sy.course_system.service.AgentService;
import com.sy.course_system.vo.agent.AgentChatResponseVO;
import com.sy.course_system.vo.agent.AgentMessageVO;
import com.sy.course_system.vo.agent.AgentSessionVO;

@Service
public class AgentServiceImpl implements AgentService {

    private static final int ACTIVE_STATUS = 1;
    private static final int DELETED_STATUS = 0;
    private static final String ROLE_USER = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";

    private final AgentSessionMapper agentSessionMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final AgentContextAssembler contextAssembler;
    private final AgentLlmClient agentLlmClient;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final AgentMapperStruct agentMapperStruct;

    public AgentServiceImpl(AgentSessionMapper agentSessionMapper,
            AgentMessageMapper agentMessageMapper,
            AgentContextAssembler contextAssembler,
            AgentLlmClient agentLlmClient,
            AgentProperties properties,
            ObjectMapper objectMapper,
            AgentMapperStruct agentMapperStruct) {
        this.agentSessionMapper = agentSessionMapper;
        this.agentMessageMapper = agentMessageMapper;
        this.contextAssembler = contextAssembler;
        this.agentLlmClient = agentLlmClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.agentMapperStruct = agentMapperStruct;
    }

    @Override
    public List<AgentSessionVO> listSessions(Long userId) {
        ensureEnabled();
        return agentMapperStruct.toSessionVOList(agentSessionMapper.selectActiveByUserId(userId));
    }

    @Override
    public AgentSessionVO createSession(Long userId, AgentSessionTitleDTO request) {
        ensureEnabled();
        AgentSession session = createSessionEntity(userId, sanitizeTitle(request == null ? null : request.getTitle(),
                "新的学习对话"));
        return agentMapperStruct.toSessionVO(session);
    }

    @Override
    public AgentSessionVO updateSession(Long userId, Long sessionId, AgentSessionTitleDTO request) {
        ensureEnabled();
        AgentSession session = requireSession(userId, sessionId);
        session.setTitle(sanitizeTitle(request == null ? null : request.getTitle(), session.getTitle()));
        agentSessionMapper.updateById(session);
        return agentMapperStruct.toSessionVO(requireSession(userId, sessionId));
    }

    @Override
    public boolean deleteSession(Long userId, Long sessionId) {
        ensureEnabled();
        AgentSession session = requireSession(userId, sessionId);
        session.setStatus(DELETED_STATUS);
        return agentSessionMapper.updateById(session) > 0;
    }

    @Override
    public List<AgentMessageVO> listMessages(Long userId, Long sessionId) {
        ensureEnabled();
        requireSession(userId, sessionId);
        return agentMapperStruct.toMessageVOList(agentMessageMapper.selectBySessionIdAndUserId(sessionId, userId));
    }

    @Override
    public AgentChatResponseVO chat(Long userId, AgentChatRequestDTO request) {
        ensureEnabled();
        String userContent = normalizeMessage(request == null ? null : request.getMessage());
        AgentSession session = resolveSession(userId, request == null ? null : request.getSessionId(), userContent);

        AgentMessage userMessage = newMessage(session.getId(), userId, ROLE_USER, userContent, null);
        agentMessageMapper.insert(userMessage);

        AgentContextSnapshot context = contextAssembler.assemble(userId);
        LocalDateTime startedAt = LocalDateTime.now();
        String assistantContent;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", properties.useMockClient() ? "mock" : properties.provider());
        metadata.put("model", properties.model());
        metadata.put("sourceCount", context.getSources().size());

        try {
            assistantContent = agentLlmClient.chat(buildLlmRequest(session.getId(), userId, context));
            metadata.put("status", "success");
        } catch (Exception ex) {
            assistantContent = "学习助手暂时无法连接模型服务，请稍后重试。你刚才的问题已经保存在会话中。";
            metadata.put("status", "error");
            metadata.put("error", ex.getClass().getSimpleName());
        }
        metadata.put("latencyMs", Duration.between(startedAt, LocalDateTime.now()).toMillis());

        AgentMessage assistantMessage = newMessage(session.getId(), userId, ROLE_ASSISTANT, assistantContent,
                toJson(metadata));
        agentMessageMapper.insert(assistantMessage);
        touchSession(session);

        AgentChatResponseVO response = new AgentChatResponseVO();
        response.setSessionId(session.getId());
        response.setUserMessage(agentMapperStruct.toMessageVO(userMessage));
        response.setAssistantMessage(agentMapperStruct.toMessageVO(assistantMessage));
        response.setSources(context.getSources());
        return response;
    }

    private AgentLlmRequest buildLlmRequest(Long sessionId, Long userId, AgentContextSnapshot context) {
        List<AgentLlmMessage> history = agentMessageMapper.selectRecentBySessionIdAndUserId(
                sessionId,
                userId,
                Math.max(1, properties.maxHistoryMessages()))
                .stream()
                .map(this::toLlmMessage)
                .toList();

        AgentLlmRequest request = new AgentLlmRequest();
        request.setSystemPrompt(context.getSystemPrompt());
        request.setFallbackSummary(context.getFallbackSummary());
        request.setMessages(history);
        return request;
    }

    private AgentLlmMessage toLlmMessage(AgentMessage message) {
        String role = ROLE_ASSISTANT.equals(message.getRole()) ? "assistant" : "user";
        return new AgentLlmMessage(role, message.getContent());
    }

    private AgentSession resolveSession(Long userId, Long sessionId, String userContent) {
        if (sessionId != null) {
            return requireSession(userId, sessionId);
        }
        return createSessionEntity(userId, buildTitleFromMessage(userContent));
    }

    private AgentSession createSessionEntity(Long userId, String title) {
        AgentSession session = new AgentSession();
        session.setUserId(userId);
        session.setTitle(title);
        session.setStatus(ACTIVE_STATUS);
        agentSessionMapper.insert(session);
        return session;
    }

    private AgentSession requireSession(Long userId, Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        AgentSession session = agentSessionMapper.selectActiveByIdAndUserId(sessionId, userId);
        if (session == null) {
            throw new SecurityException("无权访问该会话或会话不存在");
        }
        return session;
    }

    private AgentMessage newMessage(Long sessionId, Long userId, String role, String content, String metadataJson) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setMetadataJson(metadataJson);
        return message;
    }

    private void touchSession(AgentSession session) {
        AgentSession update = new AgentSession();
        update.setId(session.getId());
        agentSessionMapper.updateById(update);
    }

    private void ensureEnabled() {
        if (!properties.enabled()) {
            throw new IllegalStateException("学习助手已禁用");
        }
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        String trimmed = message.trim();
        if (trimmed.length() > 2000) {
            throw new IllegalArgumentException("消息不能超过 2000 个字符");
        }
        return trimmed;
    }

    private String sanitizeTitle(String title, String fallback) {
        String normalized = title == null || title.isBlank() ? fallback : title.trim();
        return normalized.length() <= 60 ? normalized : normalized.substring(0, 60);
    }

    private String buildTitleFromMessage(String message) {
        String compact = message.replaceAll("\\s+", " ");
        String prefix = compact.length() <= 18 ? compact : compact.substring(0, 18);
        return sanitizeTitle("关于 " + prefix, "新的学习对话");
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
