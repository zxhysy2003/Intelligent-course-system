package com.sy.course_system.dto.recommend;

public class RecommendRequestDTO {
    
    private Long targetUserId;
    private Integer topN;
    
    public Long getTargetUserId() {
        return targetUserId;
    }
    public void setTargetUserId(Long targetUserId) {
        this.targetUserId = targetUserId;
    }
    public Integer getTopN() {
        return topN;
    }
    public void setTopN(Integer topN) {
        this.topN = topN;
    }

    
}
