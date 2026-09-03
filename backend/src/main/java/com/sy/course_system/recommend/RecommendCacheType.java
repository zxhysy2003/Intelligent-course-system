package com.sy.course_system.recommend;

import com.sy.course_system.config.RecommendProperties;

public enum RecommendCacheType {
    REGULAR,
    COLD_START;

    public String cacheKey(Long userId) {
        return (this == REGULAR ? RecommendCacheKeys.V2_REGULAR_PREFIX
                : RecommendCacheKeys.V2_COLD_START_PREFIX) + userId;
    }

    public String lockKey(Long userId) {
        return (this == REGULAR ? RecommendCacheKeys.V2_REGULAR_LOCK_PREFIX
                : RecommendCacheKeys.V2_COLD_START_LOCK_PREFIX) + userId;
    }

    public long logicalTtlMinutes(RecommendProperties.Cache cache) {
        return this == REGULAR ? cache.regularTtlMinutes() : cache.coldStartTtlMinutes();
    }

    public long jitterMinutes(RecommendProperties.Cache cache) {
        return this == REGULAR ? cache.regularTtlJitterMinutes() : cache.coldStartTtlJitterMinutes();
    }
}
