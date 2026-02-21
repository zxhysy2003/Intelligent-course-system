package com.sy.course_system.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sy.course_system.dto.course.CourseQueryDTO;
import com.sy.course_system.dto.course.CourseTempDTO;
import com.sy.course_system.entity.Course;
import com.sy.course_system.entity.Knowledge;
import com.sy.course_system.entity.Tag;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {


    void insertCourseCategoryRelations(@Param("id") Long id, @Param("categoryIds") List<Integer> categoryIds);

    void insertCourseTagRelations(@Param("id") Long id, @Param("tagMap") Map<Integer, Tag> tagMap);
    
    Map<Long, Integer> countStudyByCourseIds(@Param("courseIds") List<Long> courseIds);

    Map<Long, Integer> countProgressByCourseIds(@Param("courseIds") List<Long> courseIds);
    
    Page<CourseTempDTO> selectCoursePage(Page<CourseTempDTO> page, @Param("userId") Long userId, @Param("dto") CourseQueryDTO dto);
    
    Long selectCoursePageCount(@Param("dto") CourseQueryDTO dto);

    @Select("""
            SELECT kp_id
            FROM course_knowledge_point
            WHERE course_id = #{courseId}
            """)
    List<Long> selectKnowledgePointIdsByCourseId(@Param("courseId") Long courseId);

    @Select("""
            SELECT *
            FROM knowledge_point kp
            JOIN course_knowledge_point ckp ON kp.id = ckp.kp_id
            WHERE ckp.course_id = #{courseId} AND kp.status = 1
            """)
    List<Knowledge> selectKnowledgePointsByCourseId(@Param("courseId") Long courseId);

    
    List<Course> selectCourseNamesByIds(@Param("courseIds") List<Long> courseIds);

    @Select("""
            SELECT c.*
            FROM course c
            JOIN course_knowledge_point ckp ON c.id = ckp.course_id
            WHERE ckp.kp_id = #{kpId} AND c.status = 1
            """)
    List<Course> selectCoursesByKnowledgePointId(@Param("kpId") Long kpId);

    int updateCourseStatusByBatchIds(@Param("courseIds") List<Long> courseIds, @Param("status") Integer status);

    
}
