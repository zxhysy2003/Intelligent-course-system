package com.sy.course_system.recommend;

import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;

/**
 * v2 推荐缓存内部结构。API DTO 不变，逻辑过期信息只存在于 Redis 缓存值中。
 */
public record RecommendCacheEntry(
        int schemaVersion,
        long generatedAt,
        long logicalExpireAt,
        long userVersion,
        HybridRecommendResponseDTO data) {

    public static final int CURRENT_SCHEMA_VERSION = 2;
}
