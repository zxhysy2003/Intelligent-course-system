package com.sy.course_system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sy.course_system.dto.recommend.RecommendScoreSnapshotDTO;

@Mapper
public interface RecommendScoreSnapshotMapper {

    @Insert("""
            <script>
            INSERT INTO recommend_user_course_score
                (user_id, course_id, raw_score, score, last_behavior_time)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.userId}, #{row.courseId}, #{row.rawScore}, #{row.score}, #{row.lastBehaviorTime})
            </foreach>
            ON DUPLICATE KEY UPDATE
                raw_score = VALUES(raw_score),
                score = VALUES(score),
                last_behavior_time = VALUES(last_behavior_time),
                update_time = NOW()
            </script>
            """)
    void upsertBatch(@Param("rows") List<RecommendScoreSnapshotDTO> rows);

    @Delete("""
            DELETE FROM recommend_user_course_score
            WHERE user_id = #{userId}
              AND course_id = #{courseId}
            """)
    int deleteByUserCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Delete("DELETE FROM recommend_user_course_score")
    int deleteAll();
}
