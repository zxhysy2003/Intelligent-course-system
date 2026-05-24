package com.sy.course_system.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.agent.AgentChatProcessingException;
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
import com.sy.course_system.vo.agent.AgentSourceVO;

@Service
public class AgentServiceImpl implements AgentService {

    private static final int ACTIVE_STATUS = 1;
    private static final int DELETED_STATUS = 0;
    private static final String ROLE_USER = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";
    private static final String PROCESSING_MESSAGE = "消息正在处理中，请稍后重试";
    private static final String FALLBACK_ASSISTANT_CONTENT = "学习助手暂时无法连接模型服务，请稍后重试。你刚才的问题已经保存在会话中。";
    private static final Pattern CLIENT_MESSAGE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final TypeReference<List<AgentSourceVO>> SOURCE_LIST_TYPE = new TypeReference<>() {
    };

    private final AgentSessionMapper agentSessionMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final AgentContextAssembler contextAssembler;
    private final AgentLlmClient agentLlmClient;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;
    private final AgentMapperStruct agentMapperStruct;
    private final ConcurrentMap<String, LocalDateTime> processingClientMessages = new ConcurrentHashMap<>();

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
        ChatInput input = parseChatInput(userId, request);
        AgentMessagePair existingMessages = loadMessagePair(userId, input.clientMessageId());
        if (existingMessages.hasUserMessage()) {
            return recoverOrReturnIdempotentResponse(userId, input, existingMessages);
        }

        ProcessingLease lease = acquireProcessingLease(input.processingKey());
        try {
            ResolvedSession resolvedSession = resolveSession(userId, input.sessionId(), input.message());
            AgentSession session = resolvedSession.session();
            AgentMessage userMessage = newMessage(session.getId(), userId, ROLE_USER, input.message(),
                    input.clientMessageId(), null);
            try {
                agentMessageMapper.insert(userMessage);
            } catch (DuplicateKeyException ex) {
                if (resolvedSession.created()) {
                    discardCreatedSession(session);
                }
                return buildIdempotentResponse(userId, input);
            }
            return completeChat(userId, input, userMessage);
        } finally {
            releaseProcessingLease(lease);
        }
    }

    private ChatInput parseChatInput(Long userId, AgentChatRequestDTO request) {
        String message = normalizeMessage(request == null ? null : request.getMessage());
        String clientMessageId = normalizeClientMessageId(request == null ? null : request.getClientMessageId());
        return new ChatInput(request == null ? null : request.getSessionId(), message, clientMessageId,
                processingKey(userId, clientMessageId));
    }

    private AgentChatResponseVO recoverOrReturnIdempotentResponse(Long userId, ChatInput input,
            AgentMessagePair messages) {
        ensureSameUserContent(messages.userMessage(), input.message());
        // 幂等回放也必须重新校验会话，避免删除后的隐藏会话被 clientMessageId 绕过访问。
        validateReplaySession(userId, input, messages.userMessage());
        if (messages.assistantMessage() != null) {
            return buildResponse(messages.userMessage(), messages.assistantMessage());
        }

        ProcessingLease lease = acquireProcessingLease(input.processingKey());
        try {
            AgentMessagePair latestMessages = loadMessagePair(userId, input.clientMessageId());
            if (!latestMessages.hasUserMessage()) {
                throw new AgentChatProcessingException(PROCESSING_MESSAGE);
            }
            ensureSameUserContent(latestMessages.userMessage(), input.message());
            // 拿到恢复租约后重新读取，避免原请求刚补写 ASSISTANT 时重复调用模型。
            validateReplaySession(userId, input, latestMessages.userMessage());
            if (latestMessages.assistantMessage() != null) {
                return buildResponse(latestMessages.userMessage(), latestMessages.assistantMessage());
            }
            return completeChat(userId, input, latestMessages.userMessage());
        } finally {
            releaseProcessingLease(lease);
        }
    }

    private AgentChatResponseVO completeChat(Long userId, ChatInput input, AgentMessage userMessage) {
        AssistantDraft draft = buildAssistantDraft(userId, userMessage);
        AgentMessage assistantMessage = newMessage(userMessage.getSessionId(), userId, ROLE_ASSISTANT,
                draft.content(), input.clientMessageId(), draft.metadataJson());
        try {
            agentMessageMapper.insert(assistantMessage);
        } catch (DuplicateKeyException ex) {
            return buildIdempotentResponse(userId, input);
        }
        touchSession(userMessage.getSessionId());
        return buildResponse(userMessage, assistantMessage, draft.sources());
    }

    private AssistantDraft buildAssistantDraft(Long userId, AgentMessage userMessage) {
        LocalDateTime startedAt = LocalDateTime.now();
        String assistantContent;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", properties.useMockClient() ? "mock" : properties.provider());
        metadata.put("model", properties.model());
        List<AgentSourceVO> sources = List.of();

        try {
            AgentContextSnapshot context = contextAssembler.assemble(userId);
            sources = context == null || context.getSources() == null ? List.of() : context.getSources();
            metadata.put("sourceCount", sources.size());
            metadata.put("sources", sources);
            assistantContent = agentLlmClient.chat(buildLlmRequest(userMessage, userId, context));
            metadata.put("status", "success");
        } catch (Exception ex) {
            assistantContent = FALLBACK_ASSISTANT_CONTENT;
            metadata.put("sourceCount", sources.size());
            metadata.put("sources", sources);
            metadata.put("status", "error");
            metadata.put("error", ex.getClass().getSimpleName());
        }
        metadata.put("latencyMs", Duration.between(startedAt, LocalDateTime.now()).toMillis());
        return new AssistantDraft(assistantContent, sources, toJson(metadata));
    }

    private AgentChatResponseVO buildIdempotentResponse(Long userId, ChatInput input) {
        AgentMessagePair messages = loadMessagePair(userId, input.clientMessageId());
        if (!messages.hasUserMessage()) {
            throw new AgentChatProcessingException(PROCESSING_MESSAGE);
        }
        ensureSameUserContent(messages.userMessage(), input.message());
        validateReplaySession(userId, input, messages.userMessage());
        if (messages.assistantMessage() == null) {
            throw new AgentChatProcessingException(PROCESSING_MESSAGE);
        }
        return buildResponse(messages.userMessage(), messages.assistantMessage());
    }

    private void validateReplaySession(Long userId, ChatInput input, AgentMessage userMessage) {
        requireSession(userId, userMessage.getSessionId());
        if (input.sessionId() != null && !input.sessionId().equals(userMessage.getSessionId())) {
            throw new IllegalArgumentException("客户端消息ID与会话不匹配");
        }
    }

    private AgentMessagePair loadMessagePair(Long userId, String clientMessageId) {
        AgentMessage userMessage = agentMessageMapper.selectByUserIdRoleAndClientMessageId(
                userId, ROLE_USER, clientMessageId);
        AgentMessage assistantMessage = agentMessageMapper.selectByUserIdRoleAndClientMessageId(
                userId, ROLE_ASSISTANT, clientMessageId);
        return new AgentMessagePair(userMessage, assistantMessage);
    }

    private AgentChatResponseVO buildResponse(AgentMessage userMessage, AgentMessage assistantMessage) {
        return buildResponse(userMessage, assistantMessage, readSources(assistantMessage.getMetadataJson()));
    }

    private AgentChatResponseVO buildResponse(AgentMessage userMessage, AgentMessage assistantMessage,
            List<AgentSourceVO> sources) {
        AgentChatResponseVO response = new AgentChatResponseVO();
        response.setSessionId(userMessage.getSessionId());
        response.setUserMessage(agentMapperStruct.toMessageVO(userMessage));
        response.setAssistantMessage(agentMapperStruct.toMessageVO(assistantMessage));
        response.setSources(sources);
        return response;
    }

    private AgentLlmRequest buildLlmRequest(AgentMessage userMessage, Long userId, AgentContextSnapshot context) {
        if (userMessage.getId() == null) {
            throw new IllegalStateException("用户消息ID缺失，无法构造锚定历史");
        }
        // 恢复未完成发送时，用户可能已经继续发送了后续消息；历史必须锚定到当前重试的 USER 消息。
        List<AgentLlmMessage> history = agentMessageMapper.selectRecentUntilMessageBySessionIdAndUserId(
                userMessage.getSessionId(),
                userId,
                userMessage.getId(),
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

    private ResolvedSession resolveSession(Long userId, Long sessionId, String userContent) {
        if (sessionId != null) {
            return new ResolvedSession(requireSession(userId, sessionId), false);
        }
        return new ResolvedSession(createSessionEntity(userId, buildTitleFromMessage(userContent)), true);
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

    private AgentMessage newMessage(Long sessionId, Long userId, String role, String content, String clientMessageId,
            String metadataJson) {
        AgentMessage message = new AgentMessage();
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setClientMessageId(clientMessageId);
        message.setRole(role);
        message.setContent(content);
        message.setMetadataJson(metadataJson);
        return message;
    }

    private void touchSession(Long sessionId) {
        AgentSession update = new AgentSession();
        update.setId(sessionId);
        agentSessionMapper.updateById(update);
    }

    private ProcessingLease acquireProcessingLease(String processingKey) {
        ProcessingLease lease = tryAcquireProcessingLease(processingKey);
        if (lease == null) {
            throw new AgentChatProcessingException(PROCESSING_MESSAGE);
        }
        return lease;
    }

    private ProcessingLease tryAcquireProcessingLease(String processingKey) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime existing = processingClientMessages.putIfAbsent(processingKey, now);
        if (existing == null) {
            return new ProcessingLease(processingKey, now);
        }
        if (!isProcessingExpired(existing, now)) {
            return null;
        }
        // 处理中标记过期后允许接管，用于恢复“USER 已落库但 ASSISTANT 未落库”的半成品请求。
        return processingClientMessages.replace(processingKey, existing, now)
                ? new ProcessingLease(processingKey, now)
                : null;
    }

    private boolean isProcessingExpired(LocalDateTime startedAt, LocalDateTime now) {
        long recoveryAfterMs = Math.max(0, properties.incompleteRecoveryAfterMs());
        return Duration.between(startedAt, now).toMillis() >= recoveryAfterMs;
    }

    private void releaseProcessingLease(ProcessingLease lease) {
        processingClientMessages.remove(lease.processingKey(), lease.startedAt());
    }

    private String processingKey(Long userId, String clientMessageId) {
        return userId + ":" + clientMessageId;
    }

    private void discardCreatedSession(AgentSession session) {
        if (session == null || session.getId() == null) {
            return;
        }
        try {
            AgentSession update = new AgentSession();
            update.setId(session.getId());
            update.setStatus(DELETED_STATUS);
            agentSessionMapper.updateById(update);
        } catch (Exception ignored) {
        }
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

    private void ensureSameUserContent(AgentMessage userMessage, String userContent) {
        if (!userContent.equals(userMessage.getContent())) {
            throw new IllegalArgumentException("客户端消息ID已被其他消息使用");
        }
    }

    private String normalizeClientMessageId(String clientMessageId) {
        String trimmed = clientMessageId == null ? "" : clientMessageId.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("客户端消息ID不能为空");
        }
        if (!CLIENT_MESSAGE_ID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("客户端消息ID格式不合法");
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

    private List<AgentSourceVO> readSources(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode sourcesNode = objectMapper.readTree(metadataJson).path("sources");
            if (!sourcesNode.isArray()) {
                return List.of();
            }
            List<AgentSourceVO> sources = objectMapper.convertValue(sourcesNode, SOURCE_LIST_TYPE);
            return sources == null ? List.of() : sources;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return List.of();
        }
    }

    private record ResolvedSession(AgentSession session, boolean created) {
    }

    private record ChatInput(Long sessionId, String message, String clientMessageId, String processingKey) {
    }

    private record ProcessingLease(String processingKey, LocalDateTime startedAt) {
    }

    private record AgentMessagePair(AgentMessage userMessage, AgentMessage assistantMessage) {

        private boolean hasUserMessage() {
            return userMessage != null;
        }
    }

    private record AssistantDraft(String content, List<AgentSourceVO> sources, String metadataJson) {
    }
}
