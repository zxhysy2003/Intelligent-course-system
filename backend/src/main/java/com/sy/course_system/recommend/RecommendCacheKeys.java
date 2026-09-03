package com.sy.course_system.recommend;

/** 推荐缓存 key 的唯一维护入口。 */
public final class RecommendCacheKeys {

    public static final String V2_REGULAR_PREFIX = "recommend:v2:user:";
    public static final String V2_COLD_START_PREFIX = "recommend:v2:cold:user:";
    public static final String V2_REGULAR_LOCK_PREFIX = "recommend:v2:lock:user:";
    public static final String V2_COLD_START_LOCK_PREFIX = "recommend:v2:cold:lock:user:";
    public static final String USER_VERSION_PREFIX = "recommend:v2:version:user:";
    public static final String COLD_START_STATUS_PREFIX = "recommend:cold:status:user:";

    private RecommendCacheKeys() {
    }
}
