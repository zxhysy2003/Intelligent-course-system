package com.sy.course_system.controller.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sy.course_system.agent.AgentChatProcessingException;
import com.sy.course_system.common.Result;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.UserInfo;
import com.sy.course_system.dto.agent.AgentChatRequestDTO;
import com.sy.course_system.service.AgentService;
import com.sy.course_system.vo.agent.AgentChatResponseVO;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock
    private AgentService agentService;

    private AgentController agentController;

    @BeforeEach
    void setUp() {
        agentController = new AgentController(agentService);
        UserContext.set(new UserInfo(1L, "tester", "USER"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void chatShouldReturnConflictWhenMessageIsStillProcessing() {
        when(agentService.chat(eq(1L), any(AgentChatRequestDTO.class)))
                .thenThrow(new AgentChatProcessingException("消息正在处理中，请稍后重试"));

        Result<AgentChatResponseVO> result = agentController.chat(new AgentChatRequestDTO());

        assertEquals(409, result.getCode());
        assertEquals("消息正在处理中，请稍后重试", result.getMsg());
    }
}
