package com.sy.course_system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.course_system.dto.ProgressDailyPointDTO;
import com.sy.course_system.dto.recommend.ColdStartSignalDTO;
import com.sy.course_system.dto.recommend.UserCourseBaseScoreDTO;
import com.sy.course_system.entity.LearningBehavior;



@Mapper
public interface LearningBehaviorMapper extends BaseMapper<LearningBehavior> {

    // 按天汇总用户最近 N 天的学习时长与活跃课程数。
    @Select("""
            SELECT
                DATE_FORMAT(lb.create_time, '%Y-%m-%d') AS day,
                COALESCE(SUM(CASE WHEN lb.behavior_type = 'STUDY' THEN lb.duration ELSE 0 END), 0) AS study_seconds,
                COUNT(DISTINCT lb.course_id) AS active_courses
            FROM learning_behavior lb
            WHERE lb.user_id = #{userId}
              AND lb.create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
            GROUP BY day
            ORDER BY day
            """)
    List<ProgressDailyPointDTO> listDailyProgress(@Param("userId") Long userId,
            @Param("days") Integer days);

    /**
     * 聚合用户课程分数。
     *
     * 计算公式集中维护在 LearningBehaviorMapper.xml 的 userCourseBaseScoreQuery，
     * 这里的 default 方法只负责区分“全量”和“单个用户课程”调用场景。
     */
    default List<UserCourseBaseScoreDTO> listUserCourseBaseScores() {
        return listUserCourseBaseScoresByFilter(null, null);
    }

    @Select("""
                SELECT
                    COUNT(CASE WHEN behavior_type IN ('STUDY', 'FAVORITE', 'FINISH') THEN 1 END) AS effectiveBehaviorCount,
                    COUNT(DISTINCT CASE WHEN behavior_type IN ('STUDY', 'FINISH') THEN course_id END) AS studiedCourseCount,
                    COALESCE(SUM(CASE WHEN behavior_type = 'STUDY' THEN duration ELSE 0 END), 0) AS totalStudySeconds,
                    COALESCE(SUM(CASE WHEN behavior_type = 'FINISH' THEN 1 ELSE 0 END), 0) AS finishCount
                FROM learning_behavior
                WHERE user_id = #{userId}
            """)
    // 冷启动联合信号聚合：
    // - effectiveBehaviorCount 刻意排除 VIEW，避免浏览噪声高估用户成熟度；
    // - studiedCourseCount 只统计 STUDY/FINISH 覆盖的不同课程数，用于近似用户偏好广度；
    // - totalStudySeconds 只累计 STUDY 时长，不让 FAVORITE/VIEW 对学习深度产生误导；
    // - finishCount 单独暴露，是因为“一次完课”通常比若干浅层行为更能说明用户已脱离冷启动。
    ColdStartSignalDTO selectColdStartSignal(@Param("userId") Long userId);

    List<UserCourseBaseScoreDTO> listUserCourseBaseScoresByFilter(@Param("userId") Long userId,
            @Param("courseId") Long courseId);

    // 获取单个用户单个课程的基础分数（与 listUserCourseBaseScores 公式一致）。
    default Double getUserCourseBaseScore(Long userId, Long courseId) {
        UserCourseBaseScoreDTO score = getUserCourseBaseScoreSnapshot(userId, courseId);
        return score == null ? null : score.getBaseScore();
    }

    // 获取单个用户单个课程的评分快照原始输入，保留 last_time 供业务层做时间衰减。
    default UserCourseBaseScoreDTO getUserCourseBaseScoreSnapshot(Long userId, Long courseId) {
        List<UserCourseBaseScoreDTO> scores = listUserCourseBaseScoresByFilter(userId, courseId);
        return scores.isEmpty() ? null : scores.get(0);
    }

}
