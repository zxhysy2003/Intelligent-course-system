package com.sy.course_system.dto.recommend;

import java.util.List;

public class HybridRecommendResponseDTO {
    private Long userId;
    private List<HybridRecommendItemDTO> items;
    
    public HybridRecommendResponseDTO() {
    }

    public HybridRecommendResponseDTO(Long userId, List<HybridRecommendItemDTO> items) {
        this.userId = userId;
        this.items = items;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public List<HybridRecommendItemDTO> getItems() {
        return items;
    }

    public void setItems(List<HybridRecommendItemDTO> items) {
        this.items = items;
    }

    
}
