package com.sy.course_system.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.course_system.dto.UserCourseBaseScoreDTO;
import com.sy.course_system.entity.LearningBehavior;

import io.lettuce.core.dynamic.annotation.Param;

@Mapper
public interface LearningBehaviorMapper extends BaseMapper<LearningBehavior> {

        // 用户总学习时长
        @Select("""
                        SELECT IFNULL(SUM(duration), 0)
                        FROM learning_behavior
                        WHERE user_id = #{userId}
                        AND behavior_type = 'STUDY'
                        """)
        Integer sumStudyDurationByUser(@Param("userId") Long userId);

        // 用户学习过的课程列表
        @Select("""
                        SELECT DISTINCT course_id
                        FROM learning_behavior
                        WHERE user_id = #{userId}
                        """)
        List<Long> selectLearnedCourseIds(@Param("userId") Long userId);

        // 热门课程排行
        @Select("""
                        SELECT course_id, COUNT(*) AS study_count
                        FROM learning_behavior
                        GROUP BY course_id
                        ORDER BY study_count DESC
                        LIMIT #{limit}
                        """)
        List<Map<String, Object>> hotCourses(@Param("limit") Integer limit);

        // 聚合用户课程分数
        // 计算公式：基础分值 * 衰减系数(业务层计算)
        // LOG(1 + LEAST(lb.duration, 180)) 限制单次最多算 180 分钟。
        @Select("""
                            SELECT
                                lb.user_id,
                                lb.course_id,
                                SUM(
                                    CASE
                                        WHEN lb.behavior_type = 'STUDY'
                                        THEN bw.weight * LOG(1 + LEAST(lb.duration, 180))
                                        ELSE bw.weight
                                    END
                                ) AS base_score,
                                MAX(lb.create_time) AS last_time
                            FROM learning_behavior lb
                            JOIN behavior_weight bw
                              ON lb.behavior_type = bw.behavior_type
                            GROUP BY lb.user_id, lb.course_id
                        """)
        List<UserCourseBaseScoreDTO> listUserCourseBaseScores();

}
