package com.sy.course_system.recommend;

/**
 * 推荐来源枚举，统一管理推荐链路的来源标识。
 * DTO 中 recommendSource 仍为 String，写入时使用 {@link #code()}，
 * 以兼容 Redis 历史缓存和测试数据。
 */
public enum RecommendSource {

    CF("CF"),
    COLD_START_USER("COLD_START_USER"),
    COLD_START_COURSE("COLD_START_COURSE"),
    HOT_FALLBACK("HOT_FALLBACK");

    private final String code;

    RecommendSource(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
