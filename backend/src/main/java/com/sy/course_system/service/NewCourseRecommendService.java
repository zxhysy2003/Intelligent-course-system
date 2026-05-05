package com.sy.course_system.service;

import java.util.List;

import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;

/**
 * 新课冷启动服务。
 *
 * 注意：该接口仅用于“常规推荐链路”的新课候选补充，
 * 不替代用户冷启动主分支。
 */
public interface NewCourseRecommendService {

    /**
     * 为常规用户生成已打分的新课候选。
     *
     * @param userId 用户 ID
     * @param limit 返回上限
     * @return 新课候选列表（可直接用于混排或兜底）
     */
    List<HybridRecommendItemDTO> recommendForRegularUser(Long userId, Integer limit);
}
