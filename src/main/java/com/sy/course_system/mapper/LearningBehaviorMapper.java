package com.sy.course_system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.course_system.dto.ProgressDailyPointDTO;
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
    @Select("""
                SELECT
                    lb.user_id,
                    lb.course_id,
                    (
                      -- STUDY:log(1+总时长)
                      MAX(CASE WHEN lb.behavior_type = 'STUDY' THEN bw.weight ELSE 0 END)
                      * LOG(1 + LEAST(SUM(CASE WHEN lb.behavior_type = 'STUDY' THEN lb.duration ELSE 0 END), 10800))
                      -- VIEW:按次数累计
                      + SUM(CASE WHEN lb.behavior_type = 'VIEW' THEN bw.weight ELSE 0 END)
                      -- FAVORITE:幂等
                      + MAX(CASE WHEN lb.behavior_type = 'FAVORITE' THEN bw.weight ELSE 0 END)
                      -- FINISH:幂等
                      + MAX(CASE WHEN lb.behavior_type = 'FINISH' THEN bw.weight ELSE 0 END)
                    ) AS base_score,
                    -- 不让 VIEW/FAVORITE 刷新 last_time
                    MAX(CASE WHEN lb.behavior_type IN ('STUDY', 'FINISH') THEN lb.create_time ELSE NULL END) AS last_time
                FROM learning_behavior lb
                JOIN behavior_weight bw
                  ON lb.behavior_type = bw.behavior_type
                GROUP BY lb.user_id, lb.course_id
            """)
    List<UserCourseBaseScoreDTO> listUserCourseBaseScores();

    // 获取单个用户单个课程的基础分数
    @Select("""
                SELECT
                  IFNULL(
                    MAX(CASE WHEN lb.behavior_type = 'STUDY' THEN bw.weight ELSE 0 END)
                    * LOG(1 + LEAST(SUM(CASE WHEN lb.behavior_type = 'STUDY' THEN lb.duration ELSE 0 END), 10800))
                    + SUM(CASE WHEN lb.behavior_type = 'VIEW' THEN bw.weight ELSE 0 END)
                    + MAX(CASE WHEN lb.behavior_type = 'FAVORITE' THEN bw.weight ELSE 0 END)
                    + MAX(CASE WHEN lb.behavior_type = 'FINISH' THEN bw.weight ELSE 0 END)
                  , 0) AS base_score
                FROM learning_behavior lb
                JOIN behavior_weight bw
                  ON lb.behavior_type = bw.behavior_type
                WHERE lb.user_id = #{userId}
                  AND lb.course_id = #{courseId}
                GROUP BY lb.user_id, lb.course_id
            """)
    Double getUserCourseBaseScore(@Param("userId") Long userId,
            @Param("courseId") Long courseId);

}
