package com.sy.course_system.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.sy.course_system.config.AgentProperties;
import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;
import com.sy.course_system.mapper.TagMapper;
import com.sy.course_system.mapper.UserCourseRelationMapper;
import com.sy.course_system.service.HybridRecommendService;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.service.OnboardingService;

@ExtendWith(MockitoExtension.class)
class AgentContextAssemblerTest {

    @Mock
    private OnboardingService onboardingService;
    @Mock
    private LearningAnalysisService learningAnalysisService;
    @Mock
    private HybridRecommendService hybridRecommendService;
    @Mock
    private UserCourseRelationMapper userCourseRelationMapper;
    @Mock
    private TagMapper tagMapper;

    private ThreadPoolTaskExecutor agentContextExecutor;

    @BeforeEach
    void setUp() {
        agentContextExecutor = new ThreadPoolTaskExecutor();
        agentContextExecutor.setCorePoolSize(1);
        agentContextExecutor.setMaxPoolSize(1);
        agentContextExecutor.setQueueCapacity(1);
        agentContextExecutor.setThreadNamePrefix("agent-context-test-");
        agentContextExecutor.initialize();
    }

    @AfterEach
    void tearDown() {
        agentContextExecutor.shutdown();
    }

    @Test
    void assembleShouldIncludeRecommendationSourceWhenRecommendationReturnsBeforeTimeout() {
        when(hybridRecommendService.recommend(1L)).thenReturn(recommendResponse());

        AgentContextSnapshot snapshot = buildAssembler(1000).assemble(1L);

        assertTrue(hasSource(snapshot, "recommendation"));
        assertTrue(snapshot.getSystemPrompt().contains("推荐候选 Top 1"));
    }

    @Test
    void assembleShouldSkipRecommendationSourceWhenRecommendationTimesOut() {
        when(hybridRecommendService.recommend(1L)).thenAnswer(invocation -> {
            Thread.sleep(300);
            return recommendResponse();
        });

        long startedAt = System.nanoTime();
        AgentContextSnapshot snapshot = buildAssembler(50).assemble(1L);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

        assertFalse(hasSource(snapshot, "recommendation"));
        assertTrue(elapsedMs < 250);
    }

    @Test
    void assembleShouldSkipRecommendationSourceWhenRecommendationFails() {
        when(hybridRecommendService.recommend(1L)).thenThrow(new RuntimeException("recommend unavailable"));

        AgentContextSnapshot snapshot = buildAssembler(1000).assemble(1L);

        assertFalse(hasSource(snapshot, "recommendation"));
    }

    private AgentContextAssembler buildAssembler(int recommendTimeoutMs) {
        return new AgentContextAssembler(
                onboardingService,
                learningAnalysisService,
                hybridRecommendService,
                userCourseRelationMapper,
                tagMapper,
                agentProperties(recommendTimeoutMs),
                agentContextExecutor);
    }

    private AgentProperties agentProperties(int recommendTimeoutMs) {
        return new AgentProperties(
                true,
                "mock",
                "https://example.test/v1",
                "",
                "mock-model",
                100,
                100,
                12,
                5,
                recommendTimeoutMs,
                800,
                0.3d);
    }

    private HybridRecommendResponseDTO recommendResponse() {
        HybridRecommendItemDTO item = new HybridRecommendItemDTO();
        item.setCourseId(10L);
        item.setTitle("Spring Boot 实战");
        item.setRecommendScore(88);
        item.setRecommendSource("CF");
        item.setReadiness(0.8d);
        item.setReason("根据你的学习行为推荐");
        return new HybridRecommendResponseDTO(1L, List.of(item));
    }

    private boolean hasSource(AgentContextSnapshot snapshot, String type) {
        return snapshot.getSources().stream().anyMatch(source -> type.equals(source.getType()));
    }
}
