package com.sy.course_system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
    
    
    
}
