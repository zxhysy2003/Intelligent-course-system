package com.sy.course_system.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sy.course_system.dto.CourseQueryDTO;
import com.sy.course_system.dto.CourseTempDTO;
import com.sy.course_system.entity.Course;

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
}
