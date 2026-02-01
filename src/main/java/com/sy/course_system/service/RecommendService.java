package com.sy.course_system.service;

import com.sy.course_system.dto.recommend.RecommendResponseDTO;

public interface RecommendService {
    RecommendResponseDTO recommend(Long userId);
}
