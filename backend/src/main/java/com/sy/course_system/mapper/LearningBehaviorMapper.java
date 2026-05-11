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

    // 聚合用户课程分数
    // 计算公式：基础分值 * 衰减系数(业务层计算)
    // LOG(1 + LEAST(lb.duration, 10800)) 限制单次最多算 180 分钟。
    // 收藏分不再从历史 FAVORITE 行为读取，改为读取 user_course_relation.is_favorite 当前状态。
    @Select("""
                SELECT
                    inner_t.user_id,
                    inner_t.course_id,
                    (
                      inner_t.study_score
                      + inner_t.view_score
                      + CASE WHEN COALESCE(ucr.is_favorite, 0) = 1 THEN COALESCE(fav_bw.weight, 0) ELSE 0 END
                      + inner_t.finish_score
                    ) AS base_score,
                    inner_t.last_time
                FROM (
                    SELECT
                        lb.user_id,
                        lb.course_id,
                        MAX(CASE WHEN lb.behavior_type = 'STUDY' THEN bw.weight ELSE 0 END)
                        * LOG(1 + LEAST(SUM(CASE WHEN lb.behavior_type = 'STUDY' THEN lb.duration ELSE 0 END), 10800)) AS study_score,
                        SUM(CASE WHEN lb.behavior_type = 'VIEW' THEN bw.weight ELSE 0 END) AS view_score,
                        MAX(CASE WHEN lb.behavior_type = 'FINISH' THEN bw.weight ELSE 0 END) AS finish_score,
                        MAX(CASE WHEN lb.behavior_type IN ('STUDY', 'FINISH') THEN lb.create_time ELSE NULL END) AS last_time
                    FROM learning_behavior lb
                    JOIN behavior_weight bw
                      ON lb.behavior_type = bw.behavior_type
                    GROUP BY lb.user_id, lb.course_id
                ) inner_t
                LEFT JOIN user_course_relation ucr
                  ON inner_t.user_id = ucr.user_id AND inner_t.course_id = ucr.course_id
                LEFT JOIN behavior_weight fav_bw
                  ON fav_bw.behavior_type = 'FAVORITE'
            """)
    List<UserCourseBaseScoreDTO> listUserCourseBaseScores();

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

    // 获取单个用户单个课程的基础分数（与 listUserCourseBaseScores 公式一致）
    @Select("""
                SELECT
                  IFNULL(
                    inner_t.study_score
                    + inner_t.view_score
                    + CASE WHEN COALESCE(ucr.is_favorite, 0) = 1 THEN COALESCE(fav_bw.weight, 0) ELSE 0 END
                    + inner_t.finish_score
                  , 0) AS base_score
                FROM (
                    SELECT
                        lb.user_id,
                        lb.course_id,
                        MAX(CASE WHEN lb.behavior_type = 'STUDY' THEN bw.weight ELSE 0 END)
                        * LOG(1 + LEAST(SUM(CASE WHEN lb.behavior_type = 'STUDY' THEN lb.duration ELSE 0 END), 10800)) AS study_score,
                        SUM(CASE WHEN lb.behavior_type = 'VIEW' THEN bw.weight ELSE 0 END) AS view_score,
                        MAX(CASE WHEN lb.behavior_type = 'FINISH' THEN bw.weight ELSE 0 END) AS finish_score
                    FROM learning_behavior lb
                    JOIN behavior_weight bw
                      ON lb.behavior_type = bw.behavior_type
                    WHERE lb.user_id = #{userId}
                      AND lb.course_id = #{courseId}
                    GROUP BY lb.user_id, lb.course_id
                ) inner_t
                LEFT JOIN user_course_relation ucr
                  ON inner_t.user_id = ucr.user_id AND inner_t.course_id = ucr.course_id
                LEFT JOIN behavior_weight fav_bw
                  ON fav_bw.behavior_type = 'FAVORITE'
            """)
    Double getUserCourseBaseScore(@Param("userId") Long userId,
            @Param("courseId") Long courseId);

    // 获取单个用户单个课程的评分快照原始输入，保留 last_time 供业务层做时间衰减。
    @Select("""
                SELECT
                    inner_t.user_id,
                    inner_t.course_id,
                    (
                      inner_t.study_score
                      + inner_t.view_score
                      + CASE WHEN COALESCE(ucr.is_favorite, 0) = 1 THEN COALESCE(fav_bw.weight, 0) ELSE 0 END
                      + inner_t.finish_score
                    ) AS base_score,
                    inner_t.last_time
                FROM (
                    SELECT
                        lb.user_id,
                        lb.course_id,
                        MAX(CASE WHEN lb.behavior_type = 'STUDY' THEN bw.weight ELSE 0 END)
                        * LOG(1 + LEAST(SUM(CASE WHEN lb.behavior_type = 'STUDY' THEN lb.duration ELSE 0 END), 10800)) AS study_score,
                        SUM(CASE WHEN lb.behavior_type = 'VIEW' THEN bw.weight ELSE 0 END) AS view_score,
                        MAX(CASE WHEN lb.behavior_type = 'FINISH' THEN bw.weight ELSE 0 END) AS finish_score,
                        MAX(CASE WHEN lb.behavior_type IN ('STUDY', 'FINISH') THEN lb.create_time ELSE NULL END) AS last_time
                    FROM learning_behavior lb
                    JOIN behavior_weight bw
                      ON lb.behavior_type = bw.behavior_type
                    WHERE lb.user_id = #{userId}
                      AND lb.course_id = #{courseId}
                    GROUP BY lb.user_id, lb.course_id
                ) inner_t
                LEFT JOIN user_course_relation ucr
                  ON inner_t.user_id = ucr.user_id AND inner_t.course_id = ucr.course_id
                LEFT JOIN behavior_weight fav_bw
                  ON fav_bw.behavior_type = 'FAVORITE'
            """)
    UserCourseBaseScoreDTO getUserCourseBaseScoreSnapshot(@Param("userId") Long userId,
            @Param("courseId") Long courseId);

}
