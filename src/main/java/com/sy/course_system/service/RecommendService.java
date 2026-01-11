package com.sy.course_system.service;

import com.sy.course_system.dto.RecommendResponseDTO;

public interface RecommendService {
    RecommendResponseDTO recommend(Long userId);

    
}
