package com.sy.course_system.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.util.TimeDecayUtil;
import com.sy.course_system.dto.UserCourseBaseScoreDTO;
import com.sy.course_system.dto.UserCourseScoreDTO;
import com.sy.course_system.entity.LearningBehavior;
import com.sy.course_system.enums.LearnBehaviorType;
import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.repository.KnowledgePointRepository;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.service.LearningBehaviorService;

@Service
public class LearningBehaviorServiceImpl extends ServiceImpl<LearningBehaviorMapper, LearningBehavior> implements LearningBehaviorService {
    @Autowired
    private CourseService courseService;

    @Autowired
    private KnowledgePointRepository knowledgePointRepository;

    @Autowired
    private LearningAnalysisService learningAnalysisService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public void recordBehavior(Long courseId, LearnBehaviorType behaviorType, Integer duration) {
        Long userId = UserContext.getUserId();

        // 1.记录学习行为
        LearningBehavior behavior = new LearningBehavior();
        behavior.setUserId(userId);
        behavior.setCourseId(courseId);
        behavior.setBehaviorType(behaviorType);
        behavior.setDuration(duration != null ? duration : 0);
        behavior.setCreateTime(LocalDateTime.now());

        this.save(behavior);

        // 2.更新课程热度
        double hotScore = switch (behaviorType) {
            case VIEW -> 0.5;
            case FINISH -> 2.0;
            case FAVOURITE -> 5.0;
            default -> 0.0;
        };

        learningAnalysisService.increaseCourseHot(courseId, hotScore);

        // 3. FINISH行为需要更新用户知识点掌握情况
        if (behaviorType.equals(LearnBehaviorType.FINISH)) {
            handleCourseFinished(userId, courseId);
        }
        // 4. FAVOURITE行为需要刷新推荐缓存
        if (behaviorType.equals(LearnBehaviorType.FAVOURITE)) {
            // 刷新推荐缓存
            learningAnalysisService.refreshUserRecommendCache(userId);
        }

    }

    private void handleCourseFinished(Long userId, Long courseId) {

        // 1.查询课程关联的知识点ID列表
        List<Long> kpIds = courseService.getKnowledgePointIdsByCourseId(courseId);

        if (kpIds == null || kpIds.isEmpty()) {
            return;
        }
        // 2.标记用户掌握这些知识点
        for (Long kpId : kpIds) {
            knowledgePointRepository.markUserMasteredKnowledgePoint(userId, kpId);
        }

        // 3.刷新推荐缓存(surprise)
        learningAnalysisService.refreshUserRecommendCache(userId);

    }


    @Override
    public List<LearningBehavior> listAllBehaviors() {
        return this.list();
    }

    /**
     * 计算隐式评分:在Basescore的基础上乘上时间衰减
     */
    @Override
    public List<UserCourseScoreDTO> listAggregatedScores() {
        List<UserCourseBaseScoreDTO> baseScores = baseMapper.listUserCourseBaseScores();

        return baseScores.stream().map(bs -> {
            UserCourseScoreDTO dto = new UserCourseScoreDTO();
            dto.setUserId(bs.getUserId());
            dto.setCourseId(bs.getCourseId());
            dto.setScore(bs.getBaseScore() * TimeDecayUtil.decay(bs.getLastTime()));
            return dto;
        }).toList();
    }

    /**
     * STUDY 开始
     */
    @Override
    public void startStudy(Long courseId) {
        Long userId = UserContext.getUserId();
        String key = "study:start:" + userId + ":" + courseId;
        redisTemplate.opsForValue().set(key, String.valueOf(System.currentTimeMillis()));
        // 设置过期时间为24小时
        redisTemplate.expire(key, Duration.ofHours(24));
    }

    /**
     * STUDY 结束
     */
    @Transactional
    @Override
    public void endStudy(Long courseId) {
        Long userId = UserContext.getUserId();
        String key = "study:start:" + userId + ":" + courseId;
        String startTimeStr = redisTemplate.opsForValue().get(key);
        if (startTimeStr == null) {
            // 未找到开始时间，可能是因为过期或未调用startStudy
            throw new IllegalStateException("未找到学习开始时间，请确保已调用开始学习接口");
        }
        long startTime = Long.parseLong(startTimeStr);
        long endTime = System.currentTimeMillis();
        int durationMinutes = (int) ((endTime - startTime) / 1000 / 60); // 转换为分钟
        if (durationMinutes <= 0) {
            durationMinutes = 1; // 最小记录1分钟
        }

        // 删除开始时间缓存
        redisTemplate.delete(key);

        // 记录学习行为
        LearningBehavior behavior = new LearningBehavior();
        behavior.setUserId(userId);
        behavior.setCourseId(courseId);
        behavior.setBehaviorType(LearnBehaviorType.STUDY);
        behavior.setDuration(durationMinutes);
        behavior.setCreateTime(LocalDateTime.now());
        this.save(behavior);

        // 更新课程热度
        double hotScore = durationMinutes / 30.0; // 每30分钟增加1点热度
        learningAnalysisService.increaseCourseHot(courseId, hotScore);
    }
}
