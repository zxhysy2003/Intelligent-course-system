package com.sy.course_system.service.impl;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.sy.course_system.dto.recommend.ColdStartSignalDTO;
import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.service.ColdStartSupportService;

/**
 * 冷启动判定支持服务实现。
 *
 * 判定规则（当前版本）：
 * - 仅统计 STUDY/FAVORITE/FINISH 作为有效行为，避免 VIEW 噪声过早把用户切到常规推荐；
 * - 结合学习过的课程数、总学习时长、完课次数做联合判定，而不是只看行为总条数。
 *
 * 性能策略：
 * - 先读短 TTL Redis 缓存，减少高频推荐请求下的数据库聚合压力。
 */
@Service
public class ColdStartSupportServiceImpl implements ColdStartSupportService {

    // 仅统计 STUDY/FAVORITE/FINISH 的有效行为数，避免 VIEW 过早将用户切离冷启动
    private static final long EFFECTIVE_BEHAVIOR_THRESHOLD = 3L;
    // 至少学过 2 门不同课程，才认为已有足够的课程偏好信号
    private static final long STUDIED_COURSE_THRESHOLD = 2L;
    // 总学习时长达到 10 分钟后，认为用户已经积累了基础学习信号
    private static final long TOTAL_STUDY_SECONDS_THRESHOLD = 600L;
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
     * 3) 未命中时回源数据库统计冷启动摘要信号；
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

        ColdStartSignalDTO signal = learningBehaviorMapper.selectColdStartSignal(userId);
        long effectiveBehaviorCount = signal == null || signal.getEffectiveBehaviorCount() == null
                ? 0L
                : signal.getEffectiveBehaviorCount();
        long studiedCourseCount = signal == null || signal.getStudiedCourseCount() == null
                ? 0L
                : signal.getStudiedCourseCount();
        long totalStudySeconds = signal == null || signal.getTotalStudySeconds() == null
                ? 0L
                : signal.getTotalStudySeconds();
        long finishCount = signal == null || signal.getFinishCount() == null
                ? 0L
                : signal.getFinishCount();

        // 这里采用“全部条件同时满足才视为冷启动”的保守策略：
        // 1) 一次完课通常已经足够说明用户进入了真实学习阶段，因此 finishCount 单独作为强信号；
        // 2) 学过 2 门课、累计学习 10 分钟、或有效行为达到阈值，任一满足都说明常规推荐已经开始有可用数据；
        // 3) VIEW 被排除在外，避免浅层浏览噪声过早把用户切出冷启动。
        boolean coldStart = finishCount == 0
                && studiedCourseCount < STUDIED_COURSE_THRESHOLD
                && totalStudySeconds < TOTAL_STUDY_SECONDS_THRESHOLD
                && effectiveBehaviorCount < EFFECTIVE_BEHAVIOR_THRESHOLD;
        redisTemplate.opsForValue().set(cacheKey, coldStart, COLD_START_STATUS_TTL_SECONDS, TimeUnit.SECONDS);
        return coldStart;
    }
}
