package com.sy.course_system.controller.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.common.Result;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.UserInfo;
import com.sy.course_system.converter.HybridRecommendMapperStruct;
import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;
import com.sy.course_system.service.HybridRecommendService;
import com.sy.course_system.vo.HybridRecommendResponseVO;
import com.sy.course_system.vo.KnowledgeMasteryVO;
import com.sy.course_system.vo.KnowledgeVO;

@ExtendWith(MockitoExtension.class)
class RecommendControllerTest {

    @Mock
    private HybridRecommendService hybridRecommendService;

    @InjectMocks
    private RecommendController recommendController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // 控制器层测试只关心“DTO 是否在边界被正确裁剪成 VO”，
        // 因此直接注入真实 MapStruct 实现，避免把映射行为也 mock 掉。
        ReflectionTestUtils.setField(
                recommendController,
                "hybridRecommendMapperStruct",
                Mappers.getMapper(HybridRecommendMapperStruct.class));
        UserContext.set(new UserInfo(1L, "tester", "USER"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void hybridRecommendShouldReturnFrontEndVoWithoutInternalFields() throws Exception {
        when(hybridRecommendService.recommend(1L)).thenReturn(responseDto());

        Result<HybridRecommendResponseVO> result = recommendController.hybridRecommend();

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals(1, result.getData().getItems().size());
        assertEquals(87, result.getData().getItems().get(0).getRecommendScore());
        assertEquals("根据你的学习行为推荐；当前可直接学习", result.getData().getItems().get(0).getReason());
        assertEquals(1, result.getData().getItems().get(0).getLearningPaths().size());
        assertEquals(2, result.getData().getItems().get(0).getLearningPaths().get(0).size());

        JsonNode root = objectMapper.readTree(objectMapper.writeValueAsString(result));
        JsonNode dataNode = root.path("data");
        JsonNode itemNode = dataNode.path("items").get(0);

        assertFalse(dataNode.has("userId"));
        assertTrue(itemNode.has("courseId"));
        assertTrue(itemNode.has("recommendScore"));
        assertTrue(itemNode.has("knowledgePoints"));
        assertTrue(itemNode.has("missingPrerequisitesMastery"));
        assertTrue(itemNode.has("learningPaths"));
        assertFalse(itemNode.has("coverUrl"));
        assertFalse(itemNode.has("cfScore"));
        assertFalse(itemNode.has("finalScore"));
        assertFalse(itemNode.has("recommendSource"));

        JsonNode learningPathNode = itemNode.path("learningPaths").get(0).get(0);
        assertEquals(20L, learningPathNode.path("id").asLong());
        assertEquals("Java 基础", learningPathNode.path("name").asText());
    }

    private HybridRecommendResponseDTO responseDto() {
        HybridRecommendItemDTO item = new HybridRecommendItemDTO();
        item.setCourseId(1L);
        item.setTitle("课程名");
        item.setDifficulty(2);
        item.setRecommendScore(87);
        item.setReason("根据你的学习行为推荐；当前可直接学习");
        item.setReadiness(0.8d);
        item.setIsNewCourse(Boolean.FALSE);
        item.setCoverUrl("https://example.com/cover.png");
        item.setCfScore(0.9d);
        item.setFinalScore(0.76d);
        item.setRecommendSource("CF");
        item.setKnowledgePoints(List.of(
                new KnowledgeVO(10L, "集合框架", 2),
                new KnowledgeVO(11L, "Lambda", 3)));
        item.setMissingPrerequisitesMastery(List.of(
                new KnowledgeMasteryVO(12L, "Java 语法", 1, 0.5d, 0.7d)));
        item.setLearningPaths(List.of(List.of(
                new KnowledgeVO(20L, "Java 基础", 1),
                new KnowledgeVO(21L, "面向对象", 2))));
        return new HybridRecommendResponseDTO(1L, List.of(item));
    }
}
