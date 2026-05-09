package com.sy.course_system.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.util.TimeDecayUtil;
import com.sy.course_system.dto.recommend.UserCourseBaseScoreDTO;
import com.sy.course_system.dto.recommend.UserCourseScoreDTO;
import com.sy.course_system.entity.LearningBehavior;
import com.sy.course_system.entity.UserCourseRelation;
import com.sy.course_system.enums.BehaviorHandleResult;
import com.sy.course_system.enums.LearnBehaviorType;
import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.recommend.RecommendCacheInvalidator;
import com.sy.course_system.repository.KnowledgeRepository;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.service.LearningBehaviorService;
import com.sy.course_system.service.UserCourseService;
import com.sy.course_system.service.VideoService;

@Service
public class LearningBehaviorServiceImpl extends ServiceImpl<LearningBehaviorMapper, LearningBehavior>
        implements LearningBehaviorService {

    private static final int MAX_SINGLE_SESSION_SECONDS = 6 * 60 * 60; // 每次上报的最大学习时长: 6小时
    private static final double FINISH_HOT_SCORE = 2.0; // 完成课程后增加的热度分数
    private static final double BASE_SCORE_THRESHOLD = 40.0; // 基础分数阈值

    private final CourseService courseService;
    private final KnowledgeRepository knowledgeRepository;
    private final LearningAnalysisService learningAnalysisService;
    private final UserCourseService userCourseService;
    private final VideoService videoService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RecommendCacheInvalidator recommendCacheInvalidator;

    private static final long VIEW_COOLDOWN_SECONDS = 10 * 60; // 10分钟冷却时间

    public LearningBehaviorServiceImpl(CourseService courseService,
            KnowledgeRepository knowledgeRepository,
            LearningAnalysisService learningAnalysisService,
            UserCourseService userCourseService,
            VideoService videoService,
            StringRedisTemplate stringRedisTemplate,
            RecommendCacheInvalidator recommendCacheInvalidator) {
        this.courseService = courseService;
        this.knowledgeRepository = knowledgeRepository;
        this.learningAnalysisService = learningAnalysisService;
        this.userCourseService = userCourseService;
        this.videoService = videoService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.recommendCacheInvalidator = recommendCacheInvalidator;
    }

    /**
     * 主入口方法：记录学习行为
     */
    @Override
    @Transactional(transactionManager = "transactionManager")
    public void recordBehavior(Long courseId,
            LearnBehaviorType behaviorType,
            Integer duration) {
        Long userId = UserContext.getUserId();

        int safeDuration = duration != null ? Math.min(MAX_SINGLE_SESSION_SECONDS, duration) : 0;

        // 1.先处理关系表（决定：是否忽略、是否触发FINISH）
        BehaviorHandleResult result = handleUserCourseRelation(userId, courseId, behaviorType, safeDuration);

        // 2.决定是否入库（行为表）
        // 约定： IGNORE 表示本次不写"learning_behavior"表（例如UNFAVORITE）
        if (result != BehaviorHandleResult.IGNORE) {
            saveBehavior(userId, courseId, behaviorType, safeDuration);
            // 热度： 对本行为加一次（STUDY/VIEW/FAVORITE）
            double hotScore = calcHotScore(behaviorType, safeDuration);
            learningAnalysisService.increaseCourseHot(courseId, hotScore);
        }
        if (behaviorType == LearnBehaviorType.STUDY && result == BehaviorHandleResult.NORMAL) {
            recommendCacheInvalidator.invalidateStudyUserRecommend(userId);
        }
        // 3.若首次完成：补写FINISH行为 + FINISH热度（里程碑事件）
        if (result == BehaviorHandleResult.TRIGGER_FINISH) {
            recordFinishInternal(userId, courseId);
        }
    }

    /**
     * 用户课程关系处理
     */
    private BehaviorHandleResult handleUserCourseRelation(Long userId,
            Long courseId,
            LearnBehaviorType behaviorType,
            Integer duration) {
        UserCourseRelation relation = userCourseService.getUserCourseRelation(userId, courseId);
        if (relation == null) {
            // 要求必须先选课，否则不记录行为
            return BehaviorHandleResult.IGNORE;
        }

        LocalDateTime now = LocalDateTime.now();

        return switch (behaviorType) {

            case VIEW -> {
                boolean allowed = allowViewOnceInCooldown(userId, courseId);
                if (!allowed) {
                    // 冷却中：不入库、不加热度
                    yield BehaviorHandleResult.IGNORE; // 冷却中，忽略本次VIEW行为
                }
                // 允许记录VIEW行为
                yield BehaviorHandleResult.NORMAL;
            }

            case STUDY -> handleStudy(relation, userId, courseId, duration, now);
            case FAVORITE -> {
                Integer old = relation.getIsFavorite() == null ? 0 : relation.getIsFavorite();
                if (old == 1) {
                    // 已收藏，忽略本次行为
                    yield BehaviorHandleResult.IGNORE;
                }
                relation.setIsFavorite(1); // 标记为已收藏
                relation.setLastLearnTime(now);
                userCourseService.updateUserCourseRelation(relation);

                recommendCacheInvalidator.invalidateStrongUserRecommend(userId);
                yield BehaviorHandleResult.NORMAL;
            }
            case UNFAVORITE -> {
                relation.setIsFavorite(0); // 标记为未收藏
                relation.setLastLearnTime(now);
                userCourseService.updateUserCourseRelation(relation);

                recommendCacheInvalidator.invalidateStrongUserRecommend(userId);
                yield BehaviorHandleResult.IGNORE; // 不记录该行为
            }

            default -> BehaviorHandleResult.IGNORE;
        };

    }

    // VIEW行为冷却检查
    // 成功返回 true → 允许记 VIEW；失败 false → 冷却中。
    private boolean allowViewOnceInCooldown(Long userId, Long courseId) {
        String key = "view:cooldown:" + userId + ":" + courseId;

        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, "1",
                java.time.Duration.ofSeconds(VIEW_COOLDOWN_SECONDS));

        return Boolean.TRUE.equals(ok);
    }

    /**
     * 处理学习行为（改进版：并发安全+性能优化）
     *
     * @param relation
     * @param userId
     * @param courseId
     * @param duration
     * @param now
     * @return
     */
    private BehaviorHandleResult handleStudy(UserCourseRelation relation,
            Long userId,
            Long courseId,
            Integer duration,
            LocalDateTime now) {

        Integer total = videoService.getVideoDurationInSeconds(courseId);
        if (total == null || total <= 0) {
            // 没有视频时长就只更新时间，不更新进度
            relation.setLastLearnTime(now);
            userCourseService.updateUserCourseRelation(relation);
            return BehaviorHandleResult.NORMAL;
        }

        int d = duration != null ? Math.max(duration, 0) : 0;

        // 1) 原子更新学习时长+进度（并发安全累加）
        userCourseService.addStudyTimeAndUpdateProgress(userId, courseId, d, total, now);

        // 2) 已写入完成时间才视为 FINISH 后置动作处理过，避免历史 status=2 但 complete_time 为空的数据被跳过。
        Integer oldStatus = relation.getStatus();
        if (oldStatus != null && oldStatus == 2 && relation.getCompleteTime() != null) {
            return BehaviorHandleResult.NORMAL;
        }

        // 3) 首次完成门闩：只有第一次会返回 1
        int marked = userCourseService.tryMarkFinished(userId, courseId, now);
        if (marked == 1) {
            handleCourseFinished(userId, courseId); // 更新 Neo4j MASTERED + 刷新缓存
            return BehaviorHandleResult.TRIGGER_FINISH; // 外层会写 FINISH 行为
        }

        return BehaviorHandleResult.NORMAL;
    }

    /**
     * 保存行为
     */
    private void saveBehavior(Long userId,
            Long courseId,
            LearnBehaviorType behaviorType,
            Integer duration) {
        LearningBehavior behavior = new LearningBehavior();
        behavior.setUserId(userId);
        behavior.setCourseId(courseId);
        behavior.setBehaviorType(behaviorType);
        behavior.setDuration(duration != null ? Math.min(MAX_SINGLE_SESSION_SECONDS, duration) : 0);

        this.save(behavior);
    }

    /**
     * FINISH 内部统一逻辑
     */
    private void recordFinishInternal(Long userId, Long courseId) {
        saveBehavior(userId, courseId, LearnBehaviorType.FINISH, 0);
        learningAnalysisService.increaseCourseHot(courseId, FINISH_HOT_SCORE);
    }

    /**
     * 计算热度分数
     */
    private double calcHotScore(LearnBehaviorType behaviorType, Integer duration) {
        return switch (behaviorType) {
            case VIEW -> 0.5;
            case FINISH -> FINISH_HOT_SCORE;
            case FAVORITE -> 5.0;
            case STUDY -> (Math.max(duration / 60.0, 1)) / 30.0; // 每30分钟增加1点热度
            default -> 0.0;
        };
    }

    /**
     * 完成课程处理
     */
    private void handleCourseFinished(Long userId, Long courseId) {
        recommendCacheInvalidator.invalidateStrongUserRecommend(userId);

        // 获取该课程的所有知识点ID
        List<Long> kpIds = courseService.getKnowledgePointIdsByCourseId(courseId);
        if (kpIds == null || kpIds.isEmpty()) {
            return;
        }

        // completionRate: 完成触发时基本为1，但保留写法更严谨
        UserCourseRelation relation = userCourseService.getUserCourseRelation(userId, courseId);
        Integer learnedSeconds = relation.getLearnedSeconds() != null ? relation.getLearnedSeconds() : 0;
        Integer courseTotalSeconds = videoService.getVideoDurationInSeconds(courseId);
        double completionRate = courseTotalSeconds != null && courseTotalSeconds > 0
                ? Math.min(1.0, (learnedSeconds * 1.0) / courseTotalSeconds)
                : 0.0;

        // behaviorMastery: 基于现有隐式评分公式（不加时间衰减，更符合“掌握”）
        Double baseScore = baseMapper.getUserCourseBaseScore(userId, courseId);
        double bahaviorMastery = Math.min(1.0, (baseScore == null ? 0.0 : baseScore) / BASE_SCORE_THRESHOLD);

        // 融合掌握度
        double mastery = 0.7 * completionRate + 0.3 * bahaviorMastery;
        // 标记用户掌握这些知识点
        knowledgeRepository.markUserMasteredBatch(userId, kpIds, mastery);
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
            // 使用时间衰减后的评分
            dto.setScore(bs.getBaseScore() * TimeDecayUtil.decay(bs.getLastTime()));
            return dto;
        })
                .filter(dto -> dto.getScore() >= 0.1) // 过滤掉评分过低的记录
                .toList();
    }
}
