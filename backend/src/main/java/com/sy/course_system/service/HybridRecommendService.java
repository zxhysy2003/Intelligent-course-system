package com.sy.course_system.service;

import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;

public interface HybridRecommendService {

    HybridRecommendResponseDTO recommend(Long userId);

}
