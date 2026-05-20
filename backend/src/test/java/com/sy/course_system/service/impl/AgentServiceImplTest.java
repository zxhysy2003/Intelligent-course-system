package com.sy.course_system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.agent.AgentContextAssembler;
import com.sy.course_system.agent.AgentContextSnapshot;
import com.sy.course_system.agent.AgentLlmClient;
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
            message.setId(message.getId() == null ? System.nanoTime() : message.getId());
            return 1;
        }).when(agentMessageMapper).insert(any(AgentMessage.class));
        when(contextAssembler.assemble(1L)).thenReturn(new AgentContextSnapshot(
                "system prompt",
                "fallback summary",
                List.of(new AgentSourceVO("recommendation", "混合推荐", "推荐上下文", "recommend/hybrid"))));
        when(agentMessageMapper.selectRecentBySessionIdAndUserId(eq(10L), eq(1L), eq(12))).thenReturn(List.of());
        when(agentLlmClient.chat(any())).thenReturn("这是助手回答");

        AgentChatRequestDTO request = new AgentChatRequestDTO();
        request.setMessage("我接下来学什么？");

        AgentChatResponseVO response = agentService.chat(1L, request);

        assertEquals(10L, response.getSessionId());
        assertEquals("我接下来学什么？", response.getUserMessage().getContent());
        assertEquals("这是助手回答", response.getAssistantMessage().getContent());
        assertEquals(1, response.getSources().size());

        ArgumentCaptor<AgentMessage> messageCaptor = ArgumentCaptor.forClass(AgentMessage.class);
        verify(agentMessageMapper, org.mockito.Mockito.times(2)).insert(messageCaptor.capture());
        assertEquals("USER", messageCaptor.getAllValues().get(0).getRole());
        assertEquals("ASSISTANT", messageCaptor.getAllValues().get(1).getRole());
        assertNotNull(messageCaptor.getAllValues().get(1).getMetadataJson());
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

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> disabledService.chat(1L, request));

        assertEquals("学习助手已禁用", ex.getMessage());
        verifyNoInteractions(agentSessionMapper, agentMessageMapper, contextAssembler, agentLlmClient);
    }

    private AgentServiceImpl buildService(boolean enabled) {
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
}
