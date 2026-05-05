package com.sy.course_system.dto.recommend;

import java.util.List;

public class RecommendResponseDTO {
    private Long userId;
    private List<RecommendItemDTO> items;
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public List<RecommendItemDTO> getItems() {
        return items;
    }

    public void setItems(List<RecommendItemDTO> items) {
        this.items = items;
    }
    
}
