package com.sy.course_system.service;

import java.util.List;

import com.sy.course_system.dto.agent.AgentChatRequestDTO;
import com.sy.course_system.dto.agent.AgentSessionTitleDTO;
import com.sy.course_system.vo.agent.AgentChatResponseVO;
import com.sy.course_system.vo.agent.AgentMessageVO;
import com.sy.course_system.vo.agent.AgentSessionVO;

public interface AgentService {

    List<AgentSessionVO> listSessions(Long userId);

    AgentSessionVO createSession(Long userId, AgentSessionTitleDTO request);

    AgentSessionVO updateSession(Long userId, Long sessionId, AgentSessionTitleDTO request);

    boolean deleteSession(Long userId, Long sessionId);

    List<AgentMessageVO> listMessages(Long userId, Long sessionId);

    AgentChatResponseVO chat(Long userId, AgentChatRequestDTO request);
}
