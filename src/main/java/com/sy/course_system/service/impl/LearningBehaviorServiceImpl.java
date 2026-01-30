package com.sy.course_system.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.util.TimeDecayUtil;
import com.sy.course_system.dto.UserCourseBaseScoreDTO;
import com.sy.course_system.dto.UserCourseScoreDTO;
import com.sy.course_system.entity.LearningBehavior;
import com.sy.course_system.entity.UserCourseRelation;
import com.sy.course_system.enums.BehaviorHandleResult;
import com.sy.course_system.enums.LearnBehaviorType;
import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.repository.KnowledgePointRepository;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.service.LearningBehaviorService;
import com.sy.course_system.service.UserCourseService;
import com.sy.course_system.service.VideoService;

@Service
public class LearningBehaviorServiceImpl extends ServiceImpl<LearningBehaviorMapper, LearningBehavior> implements LearningBehaviorService{
    
    private static final int MAX_SINGLE_SESSION_SECONDS = 6 * 60 * 60; // 每次上报的最大学习时长: 6小时
    private static final double FINISH_HOT_SCORE = 2.0; // 完成课程后增加的热度分数

    @Autowired
    private CourseService courseService;
    @Autowired
    private KnowledgePointRepository knowledgePointRepository;
    @Autowired
    private LearningAnalysisService learningAnalysisService;
    @Autowired
    private UserCourseService userCourseService;
    @Autowired
    private VideoService videoService;

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

        BehaviorHandleResult result = handleUserCourseRelation(userId, courseId, behaviorType, safeDuration);

        // IGNORE 行为(如UNFAVOURITE)不入库
        if (result != BehaviorHandleResult.IGNORE) {
            saveBehavior(userId, courseId, behaviorType, safeDuration);
            double hotScore = calcHotScore(behaviorType, safeDuration);
            learningAnalysisService.increaseCourseHot(courseId, hotScore);
        }
        if(result == BehaviorHandleResult.TRIGGER_FINISH) {
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
        UserCourseRelation relation =  userCourseService.getUserCourseRelation(userId, courseId);
        if (relation == null) {
            return BehaviorHandleResult.IGNORE;
        }

        LocalDateTime now = LocalDateTime.now();
        
        return switch (behaviorType) {

            case STUDY -> handleStudy(relation,userId, courseId, duration, now);
            case FAVORITE -> {
                relation.setIsFavorite(1); // 标记为已收藏
                userCourseService.updateUserCourseRelation(relation);
                learningAnalysisService.refreshUserRecommendCache(userId);
                yield BehaviorHandleResult.NORMAL;
            }
            case UNFAVORITE -> {
                relation.setIsFavorite(0); // 标记为未收藏
                userCourseService.updateUserCourseRelation(relation);
                yield BehaviorHandleResult.IGNORE;
            }

            default -> BehaviorHandleResult.IGNORE;
        };

    }

    /**
     * 处理学习行为
     */
    private BehaviorHandleResult handleStudy(UserCourseRelation relation,
                                                Long userId,
                                                Long courseId,
                                                Integer duration,
                                                LocalDateTime now) {
        // 获得进度旧值
        Integer oldProgress = relation.getProgress() != null ? relation.getProgress() : 0;
        // 获得已学习时长旧值
        Integer learnedSeconds = relation.getLearnedSeconds() != null ? relation.getLearnedSeconds() : 0;
        
        // 累加学习时长
        learnedSeconds += duration;

        // 获得视频总时长
        Integer courseTotalSeconds = videoService.getVideoDurationInSeconds(courseId);

        if (courseTotalSeconds == null || courseTotalSeconds <= 0) {
            return BehaviorHandleResult.NORMAL;
        }

        // 计算进度百分比
        Integer newProgress = (int) Math.min(100, (learnedSeconds * 100L) / courseTotalSeconds);

        // 更新关联关系
        relation.setLearnedSeconds(learnedSeconds); // 更新学习时长
        relation.setProgress(newProgress); // 更新进度
        relation.setLastLearnTime(now); // 更新最后学习时间

        // 检查是否完成课程
        if (oldProgress < 100 && newProgress == 100) {
            relation.setStatus(2); // 标记为已完成
            relation.setCompleteTime(now); // 设置完成时间
            userCourseService.updateUserCourseRelation(relation); // 保存更新
            handleCourseFinished(userId, courseId); // 处理课程完成后的逻辑
            return BehaviorHandleResult.TRIGGER_FINISH;
        } 

        userCourseService.updateUserCourseRelation(relation); // 保存更新
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
        // 获取该课程的所有知识点ID
        List<Long> kpIds = courseService.getKnowledgePointIdsByCourseId(courseId);
        // 标记用户已掌握这些知识点
        if (kpIds != null && !kpIds.isEmpty()) {
            kpIds.forEach(kp -> 
                knowledgePointRepository
                .markUserMasteredKnowledgePoint(userId, kp)
            );
        }
        // 刷新推荐缓存
        learningAnalysisService.refreshUserRecommendCache(userId);
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
}