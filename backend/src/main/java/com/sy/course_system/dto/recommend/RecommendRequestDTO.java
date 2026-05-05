package com.sy.course_system.dto.recommend;

import java.util.List;

public class RecommendRequestDTO {
    
    private Long targetUserId;
    private List<UserCourseScoreDTO> data;
    private Integer topN;
    
    public Long getTargetUserId() {
        return targetUserId;
    }
    public void setTargetUserId(Long targetUserId) {
        this.targetUserId = targetUserId;
    }
    public List<UserCourseScoreDTO> getData() {
        return data;
    }
    public void setData(List<UserCourseScoreDTO> data) {
        this.data = data;
    }
    public Integer getTopN() {
        return topN;
    }
    public void setTopN(Integer topN) {
        this.topN = topN;
    }

    
}
