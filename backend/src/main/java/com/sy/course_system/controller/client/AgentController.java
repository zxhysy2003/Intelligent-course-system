package com.sy.course_system.controller.client;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sy.course_system.common.Result;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.dto.agent.AgentChatRequestDTO;
import com.sy.course_system.dto.agent.AgentSessionTitleDTO;
import com.sy.course_system.service.AgentService;
import com.sy.course_system.vo.agent.AgentChatResponseVO;
import com.sy.course_system.vo.agent.AgentMessageVO;
import com.sy.course_system.vo.agent.AgentSessionVO;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/sessions")
    public Result<List<AgentSessionVO>> listSessions() {
        return handle(() -> agentService.listSessions(UserContext.getUserId()));
    }

    @PostMapping("/sessions")
    public Result<AgentSessionVO> createSession(@RequestBody(required = false) AgentSessionTitleDTO request) {
        return handle(() -> agentService.createSession(UserContext.getUserId(), request));
    }

    @PatchMapping("/sessions/{sessionId}")
    public Result<AgentSessionVO> updateSession(@PathVariable Long sessionId,
            @RequestBody AgentSessionTitleDTO request) {
        return handle(() -> agentService.updateSession(UserContext.getUserId(), sessionId, request));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Result<Boolean> deleteSession(@PathVariable Long sessionId) {
        return handle(() -> agentService.deleteSession(UserContext.getUserId(), sessionId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<AgentMessageVO>> listMessages(@PathVariable Long sessionId) {
        return handle(() -> agentService.listMessages(UserContext.getUserId(), sessionId));
    }

    @PostMapping("/chat")
    public Result<AgentChatResponseVO> chat(@RequestBody AgentChatRequestDTO request) {
        return handle(() -> agentService.chat(UserContext.getUserId(), request));
    }

    private <T> Result<T> handle(Supplier<T> action) {
        try {
            return Result.success(action.get());
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (SecurityException | IllegalStateException e) {
            return Result.error(403, e.getMessage());
        }
    }
}
