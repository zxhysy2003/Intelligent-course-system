package com.sy.course_system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.agent.AgentChatProcessingException;
import com.sy.course_system.agent.AgentContextAssembler;
import com.sy.course_system.agent.AgentContextSnapshot;
import com.sy.course_system.agent.AgentLlmClient;
import com.sy.course_system.agent.AgentLlmRequest;
import com.sy.course_system.config.AgentProperties;
import com.sy.course_system.converter.AgentMapperStructImpl;
import com.sy.course_system.dto.agent.AgentChatRequestDTO;
import com.sy.course_system.entity.AgentMessage;
import com.sy.course_system.entity.AgentSession;
import com.sy.course_system.mapper.AgentMessageMapper;
import com.sy.course_system.mapper.AgentSessionMapper;
import com.sy.course_system.vo.agent.AgentChatResponseVO;
import com.sy.course_system.vo.agent.AgentSourceVO;

@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

    private static final String CLIENT_MESSAGE_ID = "client-123";
    private static final String ROLE_USER = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";

    @Mock
    private AgentSessionMapper agentSessionMapper;
    @Mock
    private AgentMessageMapper agentMessageMapper;
    @Mock
    private AgentContextAssembler contextAssembler;
    @Mock
    private AgentLlmClient agentLlmClient;

    private AgentServiceImpl agentService;

    @BeforeEach
    void setUp() {
        agentService = buildService(true);
    }

    @Test
    void chatShouldCreateSessionAndPersistUserAndAssistantMessages() {
        doAnswer(invocation -> {
            AgentSession session = invocation.getArgument(0);
            session.setId(10L);
            return 1;
        }).when(agentSessionMapper).insert(any(AgentSession.class));
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(ROLE_USER.equals(message.getRole()) ? 100L : 101L);
            return 1;
        }).when(agentMessageMapper).insert(any(AgentMessage.class));
        when(contextAssembler.assemble(1L)).thenReturn(new AgentContextSnapshot(
                "system prompt",
                "fallback summary",
                List.of(new AgentSourceVO("recommendation", "混合推荐", "推荐上下文", "recommend/hybrid"))));
        when(agentMessageMapper.selectRecentUntilMessageBySessionIdAndUserId(eq(10L), eq(1L), eq(100L), eq(12)))
                .thenReturn(List.of());
        when(agentLlmClient.chat(any())).thenReturn("这是助手回答");

        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setMessage("我接下来学什么？");
        request.setClientMessageId(CLIENT_MESSAGE_ID);

        AgentChatResponseVO response = agentService.chat(1L, request);

        assertEquals(10L, response.getSessionId());
        assertEquals("我接下来学什么？", response.getUserMessage().getContent());
        assertEquals("这是助手回答", response.getAssistantMessage().getContent());
        assertEquals(1, response.getSources().size());

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(agentMessageMapper, times(2)).insert(messageCaptor.capture());
        assertEquals("USER", messageCaptor.getAllValues().get(0).getRole());
        assertEquals("ASSISTANT", messageCaptor.getAllValues().get(1).getRole());
        assertEquals(CLIENT_MESSAGE_ID, messageCaptor.getAllValues().get(0).getClientMessageId());
        assertEquals(CLIENT_MESSAGE_ID, messageCaptor.getAllValues().get(1).getClientMessageId());
        assertNotNull(messageCaptor.getAllValues().get(1).getMetadataJson());
    }

    @Test
    void chatShouldReturnExistingMessagesWhenClientMessageIdAlreadyCompleted() {
        AgentMessage userMessage = message(100L, 10L, ROLE_USER, "我接下来学什么？", CLIENT_MESSAGE_ID, null);
        AgentMessage assistantMessage = message(101L, 10L, ROLE_ASSISTANT, "这是已有回答", CLIENT_MESSAGE_ID,
                """
                        {"sources":[{"type":"recommendation","title":"混合推荐","summary":"推荐上下文","referenceId":"recommend/hybrid"}]}
                        """);
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_USER, CLIENT_MESSAGE_ID))
                .thenReturn(userMessage);
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_ASSISTANT, CLIENT_MESSAGE_ID))
                .thenReturn(assistantMessage);
        when(agentSessionMapper.selectActiveByIdAndUserId(10L, 1L)).thenReturn(activeSession(10L));

        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setMessage("我接下来学什么？");
        request.setClientMessageId(CLIENT_MESSAGE_ID);

        AgentChatResponseVO response = agentService.chat(1L, request);

        assertEquals(10L, response.getSessionId());
        assertEquals("我接下来学什么？", response.getUserMessage().getContent());
        assertEquals("这是已有回答", response.getAssistantMessage().getContent());
        assertEquals(1, response.getSources().size());
        verify(agentMessageMapper, never()).insert(any(AgentMessage.class));
        verifyNoInteractions(contextAssembler, agentLlmClient);
    }

    @Test
    void chatShouldRejectIdempotentReplayWhenStoredSessionIsDeleted() {
        AgentMessage userMessage = message(100L, 10L, ROLE_USER, "我接下来学什么？", CLIENT_MESSAGE_ID, null);
        AgentMessage assistantMessage = message(101L, 10L, ROLE_ASSISTANT, "这是已有回答", CLIENT_MESSAGE_ID,
                "{\"sources\":[]}");
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_USER, CLIENT_MESSAGE_ID))
                .thenReturn(userMessage);
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_ASSISTANT, CLIENT_MESSAGE_ID))
                .thenReturn(assistantMessage);
        when(agentSessionMapper.selectActiveByIdAndUserId(10L, 1L)).thenReturn(null);

        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setMessage("我接下来学什么？");
        request.setClientMessageId(CLIENT_MESSAGE_ID);

        SecurityException ex = assertThrows(SecurityException.class, () -> agentService.chat(1L, request));

        assertEquals("无权访问该会话或会话不存在", ex.getMessage());
        verify(agentMessageMapper, never()).insert(any(AgentMessage.class));
        verifyNoInteractions(contextAssembler, agentLlmClient);
    }

    @Test
    void chatShouldRejectIdempotentReplayWhenRequestSessionDoesNotMatchStoredMessage() {
        AgentMessage userMessage = message(100L, 10L, ROLE_USER, "我接下来学什么？", CLIENT_MESSAGE_ID, null);
        AgentMessage assistantMessage = message(101L, 10L, ROLE_ASSISTANT, "这是已有回答", CLIENT_MESSAGE_ID,
                "{\"sources\":[]}");
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_USER, CLIENT_MESSAGE_ID))
                .thenReturn(userMessage);
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_ASSISTANT, CLIENT_MESSAGE_ID))
                .thenReturn(assistantMessage);
        when(agentSessionMapper.selectActiveByIdAndUserId(10L, 1L)).thenReturn(activeSession(10L));

        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setSessionId(11L);
        request.setMessage("我接下来学什么？");
        request.setClientMessageId(CLIENT_MESSAGE_ID);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agentService.chat(1L, request));

        assertEquals("客户端消息ID与会话不匹配", ex.getMessage());
        verify(agentMessageMapper, never()).insert(any(AgentMessage.class));
        verifyNoInteractions(contextAssembler, agentLlmClient);
    }

    @Test
    void chatShouldReportProcessingWhenUserMessageExistsWithoutAssistantMessageAndProcessingMarkerIsFresh()
            throws Exception {
        AgentMessage userMessage = message(100L, 10L, ROLE_USER, "我接下来学什么？", CLIENT_MESSAGE_ID, null);
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_USER, CLIENT_MESSAGE_ID))
                .thenReturn(userMessage);
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_ASSISTANT, CLIENT_MESSAGE_ID))
                .thenReturn(null);
        when(agentSessionMapper.selectActiveByIdAndUserId(10L, 1L)).thenReturn(activeSession(10L));
        markProcessing(agentService, CLIENT_MESSAGE_ID, LocalDateTime.now());

        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setMessage("我接下来学什么？");
        request.setClientMessageId(CLIENT_MESSAGE_ID);

        AgentChatProcessingException ex = assertThrows(AgentChatProcessingException.class,
                () -> agentService.chat(1L, request));

        assertEquals("消息正在处理中，请稍后重试", ex.getMessage());
        verify(agentMessageMapper, never()).insert(any(AgentMessage.class));
        verifyNoInteractions(contextAssembler, agentLlmClient);
    }

    @Test
    void chatShouldReturnAssistantInsertedBeforeRecoveryWithoutCallingLlm() {
        AgentMessage userMessage = message(100L, 10L, ROLE_USER, "我接下来学什么？", CLIENT_MESSAGE_ID, null);
        AgentMessage assistantMessage = message(101L, 10L, ROLE_ASSISTANT, "这是已有回答", CLIENT_MESSAGE_ID,
                "{\"sources\":[]}");
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_USER, CLIENT_MESSAGE_ID))
                .thenReturn(userMessage, userMessage);
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_ASSISTANT, CLIENT_MESSAGE_ID))
                .thenReturn(null, assistantMessage);
        when(agentSessionMapper.selectActiveByIdAndUserId(10L, 1L)).thenReturn(activeSession(10L));

        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setMessage("我接下来学什么？");
        request.setClientMessageId(CLIENT_MESSAGE_ID);

        AgentChatResponseVO response = agentService.chat(1L, request);

        assertEquals("这是已有回答", response.getAssistantMessage().getContent());
        verify(agentMessageMapper, times(2))
                .selectByUserIdRoleAndClientMessageId(1L, ROLE_ASSISTANT, CLIENT_MESSAGE_ID);
        verify(agentMessageMapper, never()).insert(any(AgentMessage.class));
        verifyNoInteractions(contextAssembler, agentLlmClient);
    }

    @Test
    void chatShouldRecoverIncompleteMessageWhenNoProcessingMarkerExists() {
        AgentMessage userMessage = message(100L, 10L, ROLE_USER, "我接下来学什么？", CLIENT_MESSAGE_ID, null);
        AgentMessage laterMessage = message(102L, 10L, ROLE_USER, "后续消息", "client-456", null);
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_USER, CLIENT_MESSAGE_ID))
                .thenReturn(userMessage);
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_ASSISTANT, CLIENT_MESSAGE_ID))
                .thenReturn(null);
        when(agentSessionMapper.selectActiveByIdAndUserId(10L, 1L)).thenReturn(activeSession(10L));
        when(contextAssembler.assemble(1L)).thenReturn(contextSnapshot());
        when(agentMessageMapper.selectRecentUntilMessageBySessionIdAndUserId(eq(10L), eq(1L), eq(100L), eq(12)))
                .thenReturn(List.of(userMessage));
        when(agentLlmClient.chat(any())).thenReturn("这是恢复后的回答");
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(101L);
            return 1;
        }).when(agentMessageMapper).insert(any(AgentMessage.class));

        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setMessage("我接下来学什么？");
        request.setClientMessageId(CLIENT_MESSAGE_ID);

        AgentChatResponseVO response = agentService.chat(1L, request);

        assertEquals("这是恢复后的回答", response.getAssistantMessage().getContent());
        assertEquals("后续消息", laterMessage.getContent());
        ArgumentCaptor<AgentLlmRequest> requestCaptor = ArgumentCaptor.forClass(AgentLlmRequest.class);
        verify(agentLlmClient).chat(requestCaptor.capture());
        List<?> llmMessages = requestCaptor.getValue().getMessages();
        assertEquals("我接下来学什么？", requestCaptor.getValue().getMessages().get(llmMessages.size() - 1).getContent());
        verify(agentMessageMapper).selectRecentUntilMessageBySessionIdAndUserId(10L, 1L, 100L, 12);
        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(agentMessageMapper).insert(messageCaptor.capture());
        assertEquals(ROLE_ASSISTANT, messageCaptor.getValue().getRole());
    }

    @Test
    void chatShouldRecoverIncompleteMessageWhenProcessingMarkerExpired() throws Exception {
        AgentServiceImpl fastRecoveryService = buildService(true, 1);
        AgentMessage userMessage = message(100L, 10L, ROLE_USER, "我接下来学什么？", CLIENT_MESSAGE_ID, null);
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_USER, CLIENT_MESSAGE_ID))
                .thenReturn(userMessage);
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_ASSISTANT, CLIENT_MESSAGE_ID))
                .thenReturn(null);
        when(agentSessionMapper.selectActiveByIdAndUserId(10L, 1L)).thenReturn(activeSession(10L));
        when(contextAssembler.assemble(1L)).thenReturn(contextSnapshot());
        when(agentMessageMapper.selectRecentUntilMessageBySessionIdAndUserId(eq(10L), eq(1L), eq(100L), eq(12)))
                .thenReturn(List.of(userMessage));
        when(agentLlmClient.chat(any())).thenReturn("这是恢复后的回答");
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(101L);
            return 1;
        }).when(agentMessageMapper).insert(any(AgentMessage.class));
        markProcessing(fastRecoveryService, CLIENT_MESSAGE_ID, LocalDateTime.now().minusSeconds(1));

        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setMessage("我接下来学什么？");
        request.setClientMessageId(CLIENT_MESSAGE_ID);

        AgentChatResponseVO response = fastRecoveryService.chat(1L, request);

        assertEquals("这是恢复后的回答", response.getAssistantMessage().getContent());
    }

    @Test
    void chatShouldNotCallLlmWhenRecoveredUserMessageHasNoId() {
        AgentMessage userMessage = message(null, 10L, ROLE_USER, "我接下来学什么？", CLIENT_MESSAGE_ID, null);
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_USER, CLIENT_MESSAGE_ID))
                .thenReturn(userMessage);
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_ASSISTANT, CLIENT_MESSAGE_ID))
                .thenReturn(null);
        when(agentSessionMapper.selectActiveByIdAndUserId(10L, 1L)).thenReturn(activeSession(10L));
        when(contextAssembler.assemble(1L)).thenReturn(contextSnapshot());
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(101L);
            return 1;
        }).when(agentMessageMapper).insert(any(AgentMessage.class));

        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setMessage("我接下来学什么？");
        request.setClientMessageId(CLIENT_MESSAGE_ID);

        AgentChatResponseVO response = agentService.chat(1L, request);

        assertEquals("学习助手暂时无法连接模型服务，请稍后重试。你刚才的问题已经保存在会话中。",
                response.getAssistantMessage().getContent());
        verifyNoInteractions(agentLlmClient);
    }

    @Test
    void chatShouldRejectSameClientMessageIdWithDifferentContent() {
        AgentMessage userMessage = message(100L, 10L, ROLE_USER, "我接下来学什么？", CLIENT_MESSAGE_ID, null);
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_USER, CLIENT_MESSAGE_ID))
                .thenReturn(userMessage);

        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setMessage("请推荐一门课程");
        request.setClientMessageId(CLIENT_MESSAGE_ID);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agentService.chat(1L, request));

        assertEquals("客户端消息ID已被其他消息使用", ex.getMessage());
        verify(agentMessageMapper, never()).insert(any(AgentMessage.class));
        verifyNoInteractions(contextAssembler, agentLlmClient);
    }

    @Test
    void chatShouldPersistFallbackAssistantWhenContextAssemblyFails() {
        doAnswer(invocation -> {
            AgentSession session = invocation.getArgument(0);
            session.setId(10L);
            return 1;
        }).when(agentSessionMapper).insert(any(AgentSession.class));
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            message.setId(ROLE_USER.equals(message.getRole()) ? 100L : 101L);
            return 1;
        }).when(agentMessageMapper).insert(any(AgentMessage.class));
        when(contextAssembler.assemble(1L)).thenThrow(new RuntimeException("context unavailable"));

        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setMessage("我接下来学什么？");
        request.setClientMessageId(CLIENT_MESSAGE_ID);

        AgentChatResponseVO response = agentService.chat(1L, request);

        assertEquals("学习助手暂时无法连接模型服务，请稍后重试。你刚才的问题已经保存在会话中。",
                response.getAssistantMessage().getContent());
        verifyNoInteractions(agentLlmClient);
        verify(agentMessageMapper, times(2)).insert(any(AgentMessage.class));
    }

    @Test
    void chatShouldReturnExistingAssistantWhenAssistantInsertHitsDuplicateKey() {
        doAnswer(invocation -> {
            AgentSession session = invocation.getArgument(0);
            session.setId(10L);
            return 1;
        }).when(agentSessionMapper).insert(any(AgentSession.class));
        doAnswer(invocation -> {
            AgentMessage message = invocation.getArgument(0);
            if (ROLE_ASSISTANT.equals(message.getRole())) {
                throw new DuplicateKeyException("duplicate assistant message");
            }
            message.setId(100L);
            return 1;
        }).when(agentMessageMapper).insert(any(AgentMessage.class));
        AgentMessage userMessage = message(100L, 10L, ROLE_USER, "我接下来学什么？", CLIENT_MESSAGE_ID, null);
        AgentMessage assistantMessage = message(101L, 10L, ROLE_ASSISTANT, "这是已有回答", CLIENT_MESSAGE_ID,
                "{\"sources\":[]}");
        when(contextAssembler.assemble(1L)).thenReturn(contextSnapshot());
        when(agentMessageMapper.selectRecentUntilMessageBySessionIdAndUserId(eq(10L), eq(1L), eq(100L), eq(12)))
                .thenReturn(List.of(userMessage));
        when(agentLlmClient.chat(any())).thenReturn("这是助手回答");
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_USER, CLIENT_MESSAGE_ID))
                .thenReturn(null, userMessage);
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_ASSISTANT, CLIENT_MESSAGE_ID))
                .thenReturn(assistantMessage);
        when(agentSessionMapper.selectActiveByIdAndUserId(10L, 1L)).thenReturn(activeSession(10L));

        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setMessage("我接下来学什么？");
        request.setClientMessageId(CLIENT_MESSAGE_ID);

        AgentChatResponseVO response = agentService.chat(1L, request);

        assertEquals("这是已有回答", response.getAssistantMessage().getContent());
    }

    @Test
    void chatShouldRejectInvalidClientMessageIdBeforePersistence() {
        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setMessage("我接下来学什么？");
        request.setClientMessageId("bad id!");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> agentService.chat(1L, request));

        assertEquals("客户端消息ID格式不合法", ex.getMessage());
        verifyNoInteractions(agentSessionMapper, agentMessageMapper, contextAssembler, agentLlmClient);
    }

    @Test
    void chatShouldResolveExistingMessagesWhenUserInsertHitsDuplicateKey() {
        AgentSession session = new AgentSession();
        session.setId(10L);
        session.setUserId(1L);
        session.setStatus(1);
        AgentMessage userMessage = message(100L, 10L, ROLE_USER, "我接下来学什么？", CLIENT_MESSAGE_ID, null);
        AgentMessage assistantMessage = message(101L, 10L, ROLE_ASSISTANT, "这是已有回答", CLIENT_MESSAGE_ID,
                "{\"sources\":[]}");
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_USER, CLIENT_MESSAGE_ID))
                .thenReturn(null, userMessage);
        when(agentSessionMapper.selectActiveByIdAndUserId(10L, 1L)).thenReturn(session);
        doThrow(new DuplicateKeyException("duplicate client message"))
                .when(agentMessageMapper).insert(any(AgentMessage.class));
        when(agentMessageMapper.selectByUserIdRoleAndClientMessageId(1L, ROLE_ASSISTANT, CLIENT_MESSAGE_ID))
                .thenReturn(assistantMessage);

        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setSessionId(10L);
        request.setMessage("我接下来学什么？");
        request.setClientMessageId(CLIENT_MESSAGE_ID);

        AgentChatResponseVO response = agentService.chat(1L, request);

        assertEquals("这是已有回答", response.getAssistantMessage().getContent());
        verifyNoInteractions(contextAssembler, agentLlmClient);
    }

    @Test
    void listMessagesShouldRejectSessionOwnedByOtherUser() {
        when(agentSessionMapper.selectActiveByIdAndUserId(99L, 1L)).thenReturn(null);

        assertThrows(SecurityException.class, () -> agentService.listMessages(1L, 99L));
    }

    @Test
    void chatShouldRejectBeforePersistenceWhenAgentDisabled() {
        AgentServiceImpl disabledService = buildService(false);
        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setMessage("我接下来学什么？");
        request.setClientMessageId(CLIENT_MESSAGE_ID);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> disabledService.chat(1L, request));

        assertEquals("学习助手已禁用", ex.getMessage());
        verifyNoInteractions(agentSessionMapper, agentMessageMapper, contextAssembler, agentLlmClient);
    }

    private AgentServiceImpl buildService(boolean enabled) {
        return buildService(enabled, 90000);
    }

    private AgentServiceImpl buildService(boolean enabled, int incompleteRecoveryAfterMs) {
        AgentProperties properties = new AgentProperties(
                enabled,
                "mock",
                "https://example.test/v1",
                "",
                "mock-model",
                100,
                100,
                12,
                5,
                5000,
                incompleteRecoveryAfterMs,
                800,
                0.3d);
        return new AgentServiceImpl(
                agentSessionMapper,
                agentMessageMapper,
                contextAssembler,
                agentLlmClient,
                properties,
                new ObjectMapper(),
                new AgentMapperStructImpl());
    }

    private AgentContextSnapshot contextSnapshot() {
        return new AgentContextSnapshot(
                "system prompt",
                "fallback summary",
                List.of(new AgentSourceVO("recommendation", "混合推荐", "推荐上下文", "recommend/hybrid")));
    }

    private AgentSession activeSession(Long id) {
        AgentSession session = new AgentSession();
        session.setId(id);
        session.setUserId(1L);
        session.setStatus(1);
        return session;
    }

    private AgentMessage message(Long id, Long sessionId, String role, String content, String clientMessageId,
            String metadataJson) {
        AgentMessage message = new AgentMessage();
        message.setId(id);
        message.setSessionId(sessionId);
        message.setUserId(1L);
        message.setRole(role);
        message.setContent(content);
        message.setClientMessageId(clientMessageId);
        message.setMetadataJson(metadataJson);
        return message;
    }

    @SuppressWarnings("unchecked")
    private void markProcessing(AgentServiceImpl service, String clientMessageId, LocalDateTime startedAt)
            throws Exception {
        Field field = AgentServiceImpl.class.getDeclaredField("processingClientMessages");
        field.setAccessible(true);
        ConcurrentMap<String, LocalDateTime> processingMessages =
                (ConcurrentMap<String, LocalDateTime>) field.get(service);
        processingMessages.put("1:" + clientMessageId, startedAt);
    }

}
