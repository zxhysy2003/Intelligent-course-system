package com.sy.course_system.service.impl;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.service.ColdStartSupportService;

/**
 * 冷启动判定支持服务实现。
 *
 * 判定规则（当前版本）：
 * - 用户在 learning_behavior 表中的行为记录数小于阈值时，视为冷启动用户。
 *
 * 性能策略：
 * - 先读短 TTL Redis 缓存，减少高频推荐请求下的数据库计数压力。
 */
@Service
public class ColdStartSupportServiceImpl implements ColdStartSupportService {

    // 行为记录阈值：记录数 < 3 判定为冷启动
    private static final long COLD_START_BEHAVIOR_THRESHOLD = 3L;
    // 冷启动状态缓存 key 前缀：recommend:cold:status:user:{userId}
    private static final String COLD_START_STATUS_KEY = "recommend:cold:status:user:";
    // 冷启动状态缓存有效期（秒）
    private static final long COLD_START_STATUS_TTL_SECONDS = 120L;

    @Autowired
    private LearningBehaviorMapper learningBehaviorMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 判断用户是否为冷启动用户。
     *
     * 流程：
     * 1) 参数校验；
     * 2) 优先读取 Redis 缓存；
     * 3) 未命中时回源数据库统计行为数；
     * 4) 根据阈值判定并写回短 TTL 缓存。
     */
    @Override
    public boolean isColdStartUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        String cacheKey = COLD_START_STATUS_KEY + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof Boolean b) {
            return b;
        }
        if (cached != null) {
            return Boolean.parseBoolean(cached.toString());
        }

        Long behaviorCount = learningBehaviorMapper.countByUserId(userId);
        long safeCount = behaviorCount == null ? 0L : behaviorCount;
        boolean coldStart = safeCount < COLD_START_BEHAVIOR_THRESHOLD;
        redisTemplate.opsForValue().set(cacheKey, coldStart, COLD_START_STATUS_TTL_SECONDS, TimeUnit.SECONDS);
        return coldStart;
    }
}
