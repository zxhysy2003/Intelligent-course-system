package com.sy.course_system.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.entity.LearningBehavior;
import com.sy.course_system.entity.UserCourseRelation;
import com.sy.course_system.enums.BehaviorHandleResult;
import com.sy.course_system.enums.BehaviorRecordOutcome;
import com.sy.course_system.enums.LearnBehaviorType;
import com.sy.course_system.exception.LearningBehaviorEventConflictException;
import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.mapper.UserCourseRelationMapper;
import com.sy.course_system.outbox.LearningOutboxWriter;
import com.sy.course_system.outbox.LearningOutboxPayload;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.LearningBehaviorService;
import com.sy.course_system.service.UserCourseService;
import com.sy.course_system.service.VideoService;

@Service
public class LearningBehaviorServiceImpl extends ServiceImpl<LearningBehaviorMapper, LearningBehavior>
        implements LearningBehaviorService {

    private static final int MAX_SINGLE_SESSION_SECONDS = 6 * 60 * 60; // 每次上报的最大学习时长: 6小时
    private static final double FINISH_HOT_SCORE = 2.0; // 完成课程后增加的热度分数
    private static final double BASE_SCORE_THRESHOLD = 40.0; // 基础分数阈值
    private static final Pattern STUDY_EVENT_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");

    private final CourseService courseService;
    private final UserCourseService userCourseService;
    private final VideoService videoService;
    private final UserCourseRelationMapper relationMapper;
    private final LearningOutboxWriter outbox;
    private static final long VIEW_COOLDOWN_SECONDS = 600;

    public LearningBehaviorServiceImpl(CourseService courseService, UserCourseService userCourseService,
            VideoService videoService, UserCourseRelationMapper relationMapper, LearningOutboxWriter outbox) {
        this.courseService = courseService;
        this.userCourseService = userCourseService;
        this.videoService = videoService;
        this.relationMapper = relationMapper;
        this.outbox = outbox;
    }

    /**
     * 主入口方法：记录学习行为
     */
    @Override
    @Transactional(transactionManager = "transactionManager")
    public BehaviorRecordOutcome recordBehavior(Long courseId,
            LearnBehaviorType behaviorType,
            Integer duration,
            String eventId) {
        if (behaviorType == LearnBehaviorType.FINISH) {
            throw new IllegalArgumentException("FINISH 行为由学习进度自动生成，不能直接提交");
        }
        Long userId = UserContext.getUserId();

        int safeDuration = duration != null ? Math.min(MAX_SINGLE_SESSION_SECONDS, duration) : 0;
        String normalizedEventId = behaviorType == LearnBehaviorType.STUDY
                ? normalizeStudyEventId(eventId)
                : null;

        UserCourseRelation relation = relationMapper.selectForUpdate(userId, courseId);
        if (relation == null) {
            // 保留原接口语义：未选课时忽略行为；不占用 eventId，选课后仍可安全重试。
            return BehaviorRecordOutcome.PROCESSED;
        }

        if (behaviorType == LearnBehaviorType.STUDY) {
            LearningBehavior studyBehavior = newBehavior(
                    userId, courseId, behaviorType, safeDuration, normalizedEventId);
            int inserted = baseMapper.insertStudyIfAbsent(studyBehavior);
            if (inserted == 0) {
                LearningBehavior existing = baseMapper.selectByUserIdAndEventIdForShare(
                        userId, normalizedEventId);
                validateIdempotentReplay(existing, courseId, behaviorType, safeDuration, normalizedEventId);
                return BehaviorRecordOutcome.REPLAYED;
            }
            if (inserted != 1) {
                throw new IllegalStateException("学习事件幂等占位返回了非预期行数: " + inserted);
            }
        }

        // 1.先处理关系表（决定：是否忽略、是否触发FINISH）
        BehaviorHandleResult result = handleUserCourseRelation(
                relation, userId, courseId, behaviorType, safeDuration);

        boolean finish = result == BehaviorHandleResult.TRIGGER_FINISH;
        boolean recorded = result != BehaviorHandleResult.IGNORE;
        if (recorded && behaviorType != LearnBehaviorType.STUDY) {
            saveBehavior(userId, courseId, behaviorType, safeDuration);
        }
        // 在 FINISH 写入前冻结掌握度，保持原有隐式评分的计算时点。
        LearningOutboxPayload mastery = finish ? captureMastery(userId, courseId) : null;
        if (finish) saveBehavior(userId, courseId, LearnBehaviorType.FINISH, 0);
        if (recorded || behaviorType == LearnBehaviorType.UNFAVORITE) {
            String mode = finish || behaviorType == LearnBehaviorType.FAVORITE
                    || behaviorType == LearnBehaviorType.UNFAVORITE ? "strong"
                    : behaviorType == LearnBehaviorType.STUDY ? "soft" : null;
            double hot = recorded ? calcHotScore(behaviorType, safeDuration) + (finish ? FINISH_HOT_SCORE : 0) : 0;
            outbox.enqueue(userId, courseId, LearningOutboxPayload.of(hot,
                    mastery == null ? List.of() : mastery.knowledgePointIds(),
                    mastery == null ? null : mastery.mastery(),
                    mastery == null ? null : mastery.finishedAt(), mode), recorded, finish);
        }
        return BehaviorRecordOutcome.PROCESSED;
    }

    private String normalizeStudyEventId(String eventId) {
        String normalized = eventId == null ? "" : eventId.trim();
        if (!STUDY_EVENT_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("STUDY 行为必须提供 1-64 位合法 eventId");
        }
        return normalized;
    }

    private void validateIdempotentReplay(LearningBehavior existing,
            Long courseId,
            LearnBehaviorType behaviorType,
            Integer duration,
            String eventId) {
        if (existing == null) {
            throw new IllegalStateException("学习事件唯一键冲突后未读取到原记录");
        }
        boolean samePayload = Objects.equals(existing.getCourseId(), courseId)
                && existing.getBehaviorType() == behaviorType
                && Objects.equals(existing.getDuration(), duration);
        if (!samePayload) {
            throw new LearningBehaviorEventConflictException(
                    "eventId 已被其他学习事件使用: " + eventId);
        }
    }

    /**
     * 用户课程关系处理
     */
    private BehaviorHandleResult handleUserCourseRelation(UserCourseRelation relation,
            Long userId,
            Long courseId,
            LearnBehaviorType behaviorType,
            Integer duration) {
        LocalDateTime now = LocalDateTime.now();

        return switch (behaviorType) {

            case VIEW -> {
                boolean allowed = allowViewOnceInCooldown(relation, now);
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

                yield BehaviorHandleResult.NORMAL;
            }
            case UNFAVORITE -> {
                relation.setIsFavorite(0); // 标记为未收藏
                relation.setLastLearnTime(now);
                userCourseService.updateUserCourseRelation(relation);

                yield BehaviorHandleResult.IGNORE; // 不记录该行为
            }

            default -> BehaviorHandleResult.IGNORE;
        };

    }

    private boolean allowViewOnceInCooldown(UserCourseRelation relation, LocalDateTime now) {
        LocalDateTime last = relation.getLastViewRecordedAt();
        if (last != null && now.isBefore(last.plusSeconds(VIEW_COOLDOWN_SECONDS))) return false;
        if (relationMapper.updateViewTime(relation.getId(), now) != 1) {
            throw new IllegalStateException("浏览冷却更新失败");
        }
        return true;
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
        if (!this.save(newBehavior(userId, courseId, behaviorType, duration, null))) {
            throw new IllegalStateException("学习行为写入失败");
        }
    }

    private LearningBehavior newBehavior(Long userId,
            Long courseId,
            LearnBehaviorType behaviorType,
            Integer duration,
            String eventId) {
        LearningBehavior behavior = new LearningBehavior();
        behavior.setUserId(userId);
        behavior.setCourseId(courseId);
        behavior.setEventId(eventId);
        behavior.setBehaviorType(behaviorType);
        behavior.setDuration(duration != null ? Math.min(MAX_SINGLE_SESSION_SECONDS, duration) : 0);
        return behavior;
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
     * 冻结完课时的掌握度输入，消费端只执行该事件的确定值。
     */
    private LearningOutboxPayload captureMastery(Long userId, Long courseId) {

        // 获取该课程的所有知识点ID
        List<Long> kpIds = courseService.getKnowledgePointIdsByCourseId(courseId);
        if (kpIds == null || kpIds.isEmpty()) {
            return LearningOutboxPayload.of(0, List.of(), null, LocalDateTime.now(), null);
        }

        // completionRate: 完成触发时基本为1，但保留写法更严谨
        UserCourseRelation relation = relationMapper.selectForUpdate(userId, courseId);
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
        return LearningOutboxPayload.of(0, kpIds, mastery, relation.getCompleteTime(), null);
    }

}
