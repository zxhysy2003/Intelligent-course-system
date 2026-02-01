package com.sy.course_system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.sy.course_system.dto.recommend.RecommendRequestDTO;
import com.sy.course_system.dto.recommend.RecommendResponseDTO;
import com.sy.course_system.dto.recommend.UserCourseScoreDTO;
import com.sy.course_system.service.LearningBehaviorService;
import com.sy.course_system.service.RecommendService;

@Service
public class RecommendServiceImpl implements RecommendService {
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LearningBehaviorService learningBehaviorService;


    @Value("${recommend.service.url}")
    private String recommendServiceUrl;

    @Override
    public RecommendResponseDTO recommend(Long userId) {

        List<UserCourseScoreDTO> scoreList = learningBehaviorService.listAggregatedScores();

        // 3. 调用推荐服务
        RecommendRequestDTO request = new RecommendRequestDTO();
        request.setTargetUserId(userId);
        request.setData(scoreList);
        request.setTopN(100); // 候选池大小

        return restTemplate.postForObject(
                recommendServiceUrl + "/recommend",
                request,
                RecommendResponseDTO.class);
    }

}
