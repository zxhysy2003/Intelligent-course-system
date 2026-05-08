package com.sy.course_system.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.recommend.RecommendRequestDTO;
import com.sy.course_system.dto.recommend.RecommendResponseDTO;
import com.sy.course_system.dto.recommend.UserCourseScoreDTO;
import com.sy.course_system.service.LearningBehaviorService;
import com.sy.course_system.support.RecommendPropertiesFixture;

@ExtendWith(MockitoExtension.class)
class CfRecommendClientTest {

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private LearningBehaviorService learningBehaviorService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;

    private ObjectMapper objectMapper = new ObjectMapper();
    private RecommendProperties recommendProperties;

    private CfRecommendClient cfRecommendClient;

    @BeforeEach
    void setUp() {
        recommendProperties = defaultProperties();
        rebuildClient();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void recommendShouldUseCachedScoreMatrixWithoutAggregatingAgain() {
        List<UserCourseScoreDTO> cachedScores = List.of(score(1L, 10L, 0.8d));
        RecommendResponseDTO response = new RecommendResponseDTO();
        when(valueOperations.get("recommend:score-matrix")).thenReturn(cachedScores);
        when(restTemplate.postForObject(eq("http://recommend-service/recommend"), any(RecommendRequestDTO.class),
                eq(RecommendResponseDTO.class))).thenReturn(response);

        cfRecommendClient.recommend(1L);

        RecommendRequestDTO request = captureRequest();
        assertEquals(1L, request.getTargetUserId());
        assertEquals(100, request.getTopN());
        assertEquals(1, request.getData().size());
        assertEquals(10L, request.getData().get(0).getCourseId());
        verify(learningBehaviorService, never()).listAggregatedScores();
    }

    @Test
    void recommendShouldBuildAndCacheScoreMatrixWhenCacheMissAndLockAcquired() {
        List<UserCourseScoreDTO> scores = List.of(score(1L, 10L, 0.8d));
        RecommendResponseDTO response = new RecommendResponseDTO();
        when(valueOperations.get("recommend:score-matrix")).thenReturn(null, (Object) null);
        when(valueOperations.setIfAbsent("recommend:score-matrix:lock", "1", 20L, TimeUnit.SECONDS)).thenReturn(true);
        when(learningBehaviorService.listAggregatedScores()).thenReturn(scores);
        when(restTemplate.postForObject(eq("http://recommend-service/recommend"), any(RecommendRequestDTO.class),
                eq(RecommendResponseDTO.class))).thenReturn(response);

        cfRecommendClient.recommend(1L);

        RecommendRequestDTO request = captureRequest();
        assertScore(request.getData().get(0), 1L, 10L, 0.8d);
        verify(valueOperations).set("recommend:score-matrix", scores, 2L, TimeUnit.MINUTES);
        verify(redisTemplate).delete("recommend:score-matrix:lock");
    }

    @Test
    void recommendShouldConvertMapBackedCacheToScoreDtoList() {
        List<Map<String, Object>> cachedMaps = List.of(Map.of(
                "userId", 1L,
                "courseId", 10L,
                "score", 0.8d));
        RecommendResponseDTO response = new RecommendResponseDTO();
        when(valueOperations.get("recommend:score-matrix")).thenReturn(cachedMaps);
        when(restTemplate.postForObject(eq("http://recommend-service/recommend"), any(RecommendRequestDTO.class),
                eq(RecommendResponseDTO.class))).thenReturn(response);

        cfRecommendClient.recommend(1L);

        RecommendRequestDTO request = captureRequest();
        assertEquals(1, request.getData().size());
        assertEquals(1L, request.getData().get(0).getUserId());
        assertEquals(10L, request.getData().get(0).getCourseId());
        assertEquals(0.8d, request.getData().get(0).getScore());
        verify(learningBehaviorService, never()).listAggregatedScores();
    }

    @Test
    void recommendShouldEvictAndRebuildWhenScoreMatrixCacheCannotDeserialize() {
        List<UserCourseScoreDTO> scores = List.of(score(1L, 10L, 0.8d));
        RecommendResponseDTO response = new RecommendResponseDTO();
        when(valueOperations.get("recommend:score-matrix"))
                .thenThrow(new SerializationException("bad cache"))
                .thenReturn(null);
        when(valueOperations.setIfAbsent("recommend:score-matrix:lock", "1", 20L, TimeUnit.SECONDS)).thenReturn(true);
        when(learningBehaviorService.listAggregatedScores()).thenReturn(scores);
        when(restTemplate.postForObject(eq("http://recommend-service/recommend"), any(RecommendRequestDTO.class),
                eq(RecommendResponseDTO.class))).thenReturn(response);

        cfRecommendClient.recommend(1L);

        RecommendRequestDTO request = captureRequest();
        assertScore(request.getData().get(0), 1L, 10L, 0.8d);
        verify(redisTemplate).delete("recommend:score-matrix");
        verify(valueOperations).set("recommend:score-matrix", scores, 2L, TimeUnit.MINUTES);
    }

    @Test
    void recommendShouldEvictAndRebuildWhenScoreMatrixCacheShapeIsInvalid() {
        List<UserCourseScoreDTO> scores = List.of(score(1L, 10L, 0.8d));
        RecommendResponseDTO response = new RecommendResponseDTO();
        when(valueOperations.get("recommend:score-matrix")).thenReturn(List.of("bad-cache"), (Object) null);
        when(valueOperations.setIfAbsent("recommend:score-matrix:lock", "1", 20L, TimeUnit.SECONDS)).thenReturn(true);
        when(learningBehaviorService.listAggregatedScores()).thenReturn(scores);
        when(restTemplate.postForObject(eq("http://recommend-service/recommend"), any(RecommendRequestDTO.class),
                eq(RecommendResponseDTO.class))).thenReturn(response);

        cfRecommendClient.recommend(1L);

        RecommendRequestDTO request = captureRequest();
        assertScore(request.getData().get(0), 1L, 10L, 0.8d);
        verify(redisTemplate).delete("recommend:score-matrix");
        verify(valueOperations).set("recommend:score-matrix", scores, 2L, TimeUnit.MINUTES);
    }

    @Test
    void recommendShouldBypassRedisWhenScoreMatrixCacheDisabled() {
        List<UserCourseScoreDTO> scores = List.of(score(1L, 10L, 0.8d));
        RecommendResponseDTO response = new RecommendResponseDTO();
        recommendProperties = RecommendPropertiesFixture.builder()
                .regular(regular -> regular.serviceUrl("http://recommend-service"))
                .cache(cache -> cache.scoreMatrixEnabled(false))
                .build();
        rebuildClient();
        when(learningBehaviorService.listAggregatedScores()).thenReturn(scores);
        when(restTemplate.postForObject(eq("http://recommend-service/recommend"), any(RecommendRequestDTO.class),
                eq(RecommendResponseDTO.class))).thenReturn(response);

        cfRecommendClient.recommend(1L);

        RecommendRequestDTO request = captureRequest();
        assertScore(request.getData().get(0), 1L, 10L, 0.8d);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void recommendShouldWaitForCacheWhenAnotherThreadIsBuildingSnapshot() {
        List<UserCourseScoreDTO> cachedScores = List.of(score(1L, 10L, 0.8d));
        RecommendResponseDTO response = new RecommendResponseDTO();
        when(valueOperations.get("recommend:score-matrix")).thenReturn(null, cachedScores);
        when(valueOperations.setIfAbsent("recommend:score-matrix:lock", "1", 20L, TimeUnit.SECONDS)).thenReturn(false);
        when(restTemplate.postForObject(eq("http://recommend-service/recommend"), any(RecommendRequestDTO.class),
                eq(RecommendResponseDTO.class))).thenReturn(response);

        cfRecommendClient.recommend(1L);

        RecommendRequestDTO request = captureRequest();
        assertScore(request.getData().get(0), 1L, 10L, 0.8d);
        verify(learningBehaviorService, never()).listAggregatedScores();
    }

    @Test
    void recommendShouldUseInjectedRequestTopN() {
        List<UserCourseScoreDTO> scores = List.of(score(1L, 10L, 0.8d));
        RecommendResponseDTO response = new RecommendResponseDTO();
        recommendProperties = RecommendPropertiesFixture.builder()
                .regular(regular -> regular.serviceUrl("http://recommend-service").requestTopN(50))
                .build();
        rebuildClient();
        when(learningBehaviorService.listAggregatedScores()).thenReturn(scores);
        when(restTemplate.postForObject(eq("http://recommend-service/recommend"), any(RecommendRequestDTO.class),
                eq(RecommendResponseDTO.class))).thenReturn(response);

        cfRecommendClient.recommend(1L);

        RecommendRequestDTO request = captureRequest();
        assertEquals(50, request.getTopN());
    }

    private RecommendProperties defaultProperties() {
        return RecommendPropertiesFixture.builder()
                .regular(regular -> regular.serviceUrl("http://recommend-service").requestTopN(100))
                .cache(cache -> cache.scoreMatrixEnabled(true).scoreMatrixTtlMinutes(2L))
                .build();
    }

    private void rebuildClient() {
        cfRecommendClient = new CfRecommendClient(restTemplate, learningBehaviorService, redisTemplate,
                objectMapper, recommendProperties);
    }

    private RecommendRequestDTO captureRequest() {
        ArgumentCaptor<RecommendRequestDTO> captor = ArgumentCaptor.forClass(RecommendRequestDTO.class);
        verify(restTemplate).postForObject(eq("http://recommend-service/recommend"), captor.capture(),
                eq(RecommendResponseDTO.class));
        return captor.getValue();
    }

    private UserCourseScoreDTO score(Long userId, Long courseId, Double score) {
        UserCourseScoreDTO dto = new UserCourseScoreDTO();
        dto.setUserId(userId);
        dto.setCourseId(courseId);
        dto.setScore(score);
        return dto;
    }

    private void assertScore(UserCourseScoreDTO dto, Long userId, Long courseId, Double score) {
        assertEquals(userId, dto.getUserId());
        assertEquals(courseId, dto.getCourseId());
        assertEquals(score, dto.getScore());
    }
}
