package com.sy.course_system.vo;

import java.util.List;

/**
 * 推荐页正式响应体。
 *
 * 对于当前登录用户的推荐接口，不再额外返回 userId，
 * 避免把“请求上下文已知信息”重复放进接口协议。
 */
public class HybridRecommendResponseVO {
    private List<HybridRecommendItemVO> items;

    public List<HybridRecommendItemVO> getItems() {
        return items;
    }

    public void setItems(List<HybridRecommendItemVO> items) {
        this.items = items;
    }
}
