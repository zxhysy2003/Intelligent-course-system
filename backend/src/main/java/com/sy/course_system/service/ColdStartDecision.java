package com.sy.course_system.service;

/**
 * 推荐入口的冷启动判定结果。
 *
 * UNAVAILABLE 表示 Redis 状态缓存不可用。此时调用方必须立即走内存降级，
 * 不得为了完成判定再回源 MySQL，以免缓存故障放大为数据库雪崩。
 */
public enum ColdStartDecision {
    COLD_START,
    REGULAR,
    UNAVAILABLE
}
