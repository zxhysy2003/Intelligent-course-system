package com.sy.course_system.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sy.course_system.dto.CourseQueryDTO;
import com.sy.course_system.dto.CourseTempDTO;
import com.sy.course_system.entity.Course;
import com.sy.course_system.entity.UserCourseRelation;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    @Select("""
            SELECT kp_id
            FROM course_knowledge_point
            WHERE course_id = #{courseId}
            """)
    List<Long> selectKnowledgePointIdsByCourseId(@Param("courseId") Long courseId);

    void insertCourseCategoryRelations(@Param("id") Long id, @Param("categoryIds") List<Integer> categoryIds);

    Map<Long, Integer> countStudyByCourseIds(@Param("courseIds") List<Long> courseIds);

    Map<Long, Integer> countProgressByCourseIds(@Param("courseIds") List<Long> courseIds);
    
    Page<CourseTempDTO> selectCoursePage(Page<CourseTempDTO> page, @Param("userId") Long userId, @Param("dto") CourseQueryDTO dto);
    
    Long selectCoursePageCount(@Param("dto") CourseQueryDTO dto);

    @Select("""
            SELECT COUNT(*)
            FROM user_course_relation
            WHERE user_id = #{userId} AND course_id = #{courseId}
            """)
    Long countUserAttendCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Insert("""
            INSERT INTO user_course_relation (user_id, course_id, progress, learned_seconds, status, last_learn_time, complete_time, is_favorite)
            VALUES (#{relation.userId}, #{relation.courseId}, #{relation.progress}, #{relation.learnedSeconds}, #{relation.status}, #{relation.lastLearnTime}, #{relation.completeTime}, #{relation.isFavorite})
            """)
    void insertUserCourseRelation(@Param("relation") UserCourseRelation relation);
}
