package com.sy.course_system.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.course_system.dto.ProgressSummaryDTO;
import com.sy.course_system.dto.agent.AgentCourseProgressDTO;
import com.sy.course_system.entity.UserCourseRelation;

import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserCourseRelationMapper extends BaseMapper<UserCourseRelation> {

    @Select("SELECT * FROM user_course_relation WHERE user_id=#{userId} AND course_id=#{courseId} FOR UPDATE")
    UserCourseRelation selectForUpdate(@Param("userId") Long userId, @Param("courseId") Long courseId);

    // 保留关系不存在、用户删除和课程状态的联合判定，供异步任务判断业务是否仍有效。
    @Select("""
            SELECT c.status FROM user_course_relation r JOIN course c ON c.id=r.course_id
            JOIN user u ON u.id=r.user_id
            WHERE r.user_id=#{userId} AND r.course_id=#{courseId} AND u.deleted=0
            """)
    Integer selectCourseStatusForActiveRelation(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Update("UPDATE user_course_relation SET last_view_recorded_at=#{now} WHERE id=#{id}")
    int updateViewTime(@Param("id") Long id, @Param("now") LocalDateTime now);

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

    @Select("""
            SELECT
                ucr.course_id AS courseId,
                c.title AS title,
                c.difficulty AS difficulty,
                ucr.progress AS progress,
                ucr.learned_seconds AS learnedSeconds,
                ucr.status AS status,
                ucr.is_favorite AS favorite,
                ucr.last_learn_time AS lastLearnTime
            FROM user_course_relation ucr
            JOIN course c ON c.id = ucr.course_id
            WHERE ucr.user_id = #{userId}
              AND c.status = 1
            ORDER BY COALESCE(ucr.last_learn_time, ucr.update_time, ucr.create_time) DESC, ucr.id DESC
            LIMIT #{limit}
            """)
    List<AgentCourseProgressDTO> selectRecentCourseProgress(@Param("userId") Long userId,
            @Param("limit") Integer limit);

}
