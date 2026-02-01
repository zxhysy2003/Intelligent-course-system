package com.sy.course_system.controller.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sy.course_system.common.UserContext;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;
import com.sy.course_system.service.HybridRecommendService;

@RestController
@RequestMapping("/recommend")
public class RecommendController {

    @Autowired
    private HybridRecommendService hybridRecommendService;

    @GetMapping("/hybrid")
    public HybridRecommendResponseDTO hybridRecommend() {
        Long userId = UserContext.getUserId();
        return hybridRecommendService.recommend(userId);
    }
}
