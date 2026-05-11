package com.sy.course_system.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.recommend.RecommendRequestDTO;
import com.sy.course_system.dto.recommend.RecommendResponseDTO;

/**
 * 外部协同过滤推荐服务客户端。
 *
 * 负责调用外部 CF 推荐服务。
 *
 * 评分矩阵与 SVD 模型已经下沉到 recommend-service，本客户端只传目标用户与候选数量；
 * 推荐融合、过滤和图谱解释仍由 HybridRecommendServiceImpl 负责。
 */
@Component
public class CfRecommendClient {

    private final RestTemplate restTemplate;
    private final RecommendProperties recommendProperties;

    public CfRecommendClient(RestTemplate restTemplate,
            RecommendProperties recommendProperties) {
        this.restTemplate = restTemplate;
        this.recommendProperties = recommendProperties;
    }

    public RecommendResponseDTO recommend(Long userId) {
        RecommendRequestDTO request = new RecommendRequestDTO();
        request.setTargetUserId(userId);
        request.setTopN(recommendProperties.regular().requestTopN());

        return restTemplate.postForObject(
                recommendProperties.regular().serviceUrl() + "/recommend",
                request,
                RecommendResponseDTO.class);
    }

}
