package com.sy.course_system.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.course_system.dto.ProgressSummaryDTO;
import com.sy.course_system.entity.UserCourseRelation;

import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserCourseRelationMapper extends BaseMapper<UserCourseRelation> {

    // 获取单个用户的课程进度汇总数据
    @Select("""
            SELECT
                COUNT(*) AS total_courses,
                SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) AS finished_courses,
                ROUND(AVG(progress), 2) AS avg_progress,
                COALESCE(SUM(learned_seconds), 0) AS total_learned_seconds
            FROM user_course_relation
            WHERE user_id = #{userId}
            """)
    ProgressSummaryDTO getUserProgressSummary(@Param("userId") Long userId);

    // 更新用户课程关系（如进度、状态等）
    int updateUserCourseRelation(@Param("relation") UserCourseRelation relation);

    // 增加学习时长并更新进度（原子操作）
    int addStudyTimeAndUpdateProgress(@Param("userId") Long userId,
            @Param("courseId") Long courseId,
            @Param("duration") int duration,
            @Param("totalSeconds") int totalSeconds,
            @Param("now") LocalDateTime now);

    // 尝试标记课程完成（满足条件则更新状态）
    int tryMarkFinished(@Param("userId") Long userId,
            @Param("courseId") Long courseId,
            @Param("now") LocalDateTime now);

    // 从候选课程中查询当前用户已选的课程 ID 集合，用于推荐过滤
    @Select("""
            <script>
            SELECT course_id
            FROM user_course_relation
            WHERE user_id = #{userId}
              AND course_id IN
              <foreach collection="courseIds" item="id" open="(" separator="," close=")">
                  #{id}
              </foreach>
            </script>
            """)
    List<Long> selectSelectedCourseIds(@Param("userId") Long userId,
            @Param("courseIds") List<Long> courseIds);

}
