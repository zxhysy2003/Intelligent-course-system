package com.sy.course_system.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.recommend.RecommendRequestDTO;
import com.sy.course_system.dto.recommend.RecommendResponseDTO;
import com.sy.course_system.support.RecommendPropertiesFixture;

@ExtendWith(MockitoExtension.class)
class CfRecommendClientTest {

    @Mock
    private RestTemplate restTemplate;

    private RecommendProperties recommendProperties;
    private CfRecommendClient cfRecommendClient;

    @BeforeEach
    void setUp() {
        recommendProperties = RecommendPropertiesFixture.builder()
                .regular(regular -> regular.serviceUrl("http://recommend-service").requestTopN(100))
                .build();
        cfRecommendClient = new CfRecommendClient(restTemplate, recommendProperties);
    }

    @Test
    void recommendShouldSendOnlyTargetUserAndTopN() {
        RecommendResponseDTO response = new RecommendResponseDTO();
        when(restTemplate.postForObject(eq("http://recommend-service/recommend"), captureRequest(),
                eq(RecommendResponseDTO.class))).thenReturn(response);

        cfRecommendClient.recommend(1L);

        RecommendRequestDTO request = requestCaptor.getValue();
        assertEquals(1L, request.getTargetUserId());
        assertEquals(100, request.getTopN());
    }

    @Test
    void recommendShouldUseConfiguredTopN() {
        recommendProperties = RecommendPropertiesFixture.builder()
                .regular(regular -> regular.serviceUrl("http://recommend-service").requestTopN(50))
                .build();
        cfRecommendClient = new CfRecommendClient(restTemplate, recommendProperties);
        RecommendResponseDTO response = new RecommendResponseDTO();
        when(restTemplate.postForObject(eq("http://recommend-service/recommend"), captureRequest(),
                eq(RecommendResponseDTO.class))).thenReturn(response);

        cfRecommendClient.recommend(1L);

        assertEquals(50, requestCaptor.getValue().getTopN());
    }

    private final ArgumentCaptor<RecommendRequestDTO> requestCaptor = ArgumentCaptor.forClass(RecommendRequestDTO.class);

    private RecommendRequestDTO captureRequest() {
        return requestCaptor.capture();
    }
}
