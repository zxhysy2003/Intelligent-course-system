package com.sy.course_system.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.UserInfo;
import com.sy.course_system.controller.client.AgentController;
import com.sy.course_system.controller.client.LearningAnalysisController;
import com.sy.course_system.controller.client.OnboardingController;
import com.sy.course_system.controller.client.RecommendController;
import com.sy.course_system.controller.client.UserController;
import com.sy.course_system.converter.HybridRecommendMapperStructImpl;
import com.sy.course_system.dto.agent.AgentSessionTitleDTO;
import com.sy.course_system.dto.onboarding.OnboardingSubmitDTO;
import com.sy.course_system.service.AgentService;
import com.sy.course_system.service.HybridRecommendService;
import com.sy.course_system.service.KnowledgeGraphService;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.service.OnboardingService;

@ExtendWith(MockitoExtension.class)
class ClientApiV1ReadContractTest {
    @Mock
    private AgentService agentService;
    @Mock
    private HybridRecommendService hybridRecommendService;
    @Mock
    private KnowledgeGraphService knowledgeGraphService;
    @Mock
    private LearningAnalysisService learningAnalysisService;
    @Mock
    private OnboardingService onboardingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LearningAnalysisController analysisController = new LearningAnalysisController();
        ReflectionTestUtils.setField(analysisController, "knowledgeGraphService", knowledgeGraphService);
        ReflectionTestUtils.setField(analysisController, "learningAnalysisService", learningAnalysisService);

        OnboardingController onboardingController = new OnboardingController();
        ReflectionTestUtils.setField(onboardingController, "onboardingService", onboardingService);

        mockMvc = MockMvcBuilders.standaloneSetup(
                new UserController(),
                new AgentController(agentService),
                new RecommendController(hybridRecommendService, new HybridRecommendMapperStructImpl()),
                analysisController,
                onboardingController)
                .build();
        UserContext.set(new UserInfo(5L, "student", "STUDENT"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void exposesCurrentUserRecommendationAndAnalysisResources() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(5));
        mockMvc.perform(get("/api/v1/recommendations"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/learning-analytics/progress").param("days", "14"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/learning-analytics/ability-radar"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/learning-analytics/knowledge-graph")
                .param("courseId", "7")
                .param("depth", "2"))
                .andExpect(status().isOk());

        verify(hybridRecommendService).recommend(5L);
        verify(learningAnalysisService).getProgressChart(5L, 14);
        verify(learningAnalysisService).getAbilityRadar(5L);
        verify(knowledgeGraphService).getKnowledgeGraph(7L, 5L, 2);
    }

    @Test
    void exposesOnboardingProfileAsRepeatablePut() throws Exception {
        mockMvc.perform(get("/api/v1/onboarding/options"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/onboarding/status"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/onboarding/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentLevel\":2,\"tagIds\":[1]}"))
                .andExpect(status().isOk());

        verify(onboardingService).submit(eq(5L), any(OnboardingSubmitDTO.class));
    }

    @Test
    void exposesAssistantSessionsAndMessageSendResources() throws Exception {
        when(agentService.listSessions(5L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/assistant/sessions"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/assistant/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"计划\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/assistant/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"如何学习\",\"clientMessageId\":\"client_123456\"}"))
                .andExpect(status().isOk());

        verify(agentService).createSession(eq(5L), any(AgentSessionTitleDTO.class));
        verify(agentService).chat(eq(5L), any());
    }

    @Test
    void removedClientPathsAreNotRegistered() throws Exception {
        mockMvc.perform(get("/user/profile")).andExpect(status().isNotFound());
        mockMvc.perform(get("/recommend/hybrid")).andExpect(status().isNotFound());
        mockMvc.perform(get("/analysis/progress")).andExpect(status().isNotFound());
        mockMvc.perform(get("/agent/sessions")).andExpect(status().isNotFound());
        mockMvc.perform(post("/onboarding/submit")).andExpect(status().isNotFound());
    }
}
