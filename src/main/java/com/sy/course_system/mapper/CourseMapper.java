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
        /**
         * 为课程批量插入分类关联关系。
         */
    void insertCourseCategoryRelations(@Param("id") Long id, @Param("categoryIds") List<Integer> categoryIds);

        /**
         * 为课程批量插入标签关联关系。
         */
    void insertCourseTagRelations(@Param("id") Long id, @Param("tagMap") Map<Integer, Tag> tagMap);

    
    /**
     * 根据筛选条件分页查询课程（带用户上下文）。
     */
    Page<CourseTempDTO> selectCoursePage(Page<CourseTempDTO> page, @Param("userId") Long userId, @Param("dto") CourseQueryDTO dto);
    
    /**
     * 统计分页查询的总数（与分页查询条件一致）。
     */
    Long selectCoursePageCount(@Param("dto") CourseQueryDTO dto);

    /**
     * 获取课程关联的知识点 id 列表。
     */
    @Select("""
            SELECT kp_id
            FROM course_knowledge_point
            WHERE course_id = #{courseId}
            """)
    List<Long> selectKnowledgePointIdsByCourseId(@Param("courseId") Long courseId);

    /**
     * 获取课程关联的有效知识点列表。
     */
    @Select("""
            SELECT *
            FROM knowledge_point kp
            JOIN course_knowledge_point ckp ON kp.id = ckp.kp_id
            WHERE ckp.course_id = #{courseId} AND kp.status = 1
            """)
    List<Knowledge> selectKnowledgePointsByCourseId(@Param("courseId") Long courseId);

    
    /**
     * 按课程 id 获取课程名称信息。
     */
    List<Course> selectCourseNamesByIds(@Param("courseIds") List<Long> courseIds);

    /**
     * 获取与知识点关联的有效课程列表。
     */
    @Select("""
            SELECT c.*
            FROM course c
            JOIN course_knowledge_point ckp ON c.id = ckp.course_id
            WHERE ckp.kp_id = #{kpId} AND c.status = 1
            """)
    List<Course> selectCoursesByKnowledgePointId(@Param("kpId") Long kpId);

    /**
     * 按课程 id 批量更新课程状态。
     */
    int updateCourseStatusByBatchIds(@Param("courseIds") List<Long> courseIds, @Param("status") Integer status);

    /**
     * 为课程批量插入知识点关联关系。
     */
    void insertCourseKnowledgePointRelations(@Param("courseId") Long courseId, @Param("knowledgePointIds") List<Long> knowledgePointIds);

    void deleteCourseCategoryRelations(@Param("courseId") Long courseId);

    void deleteCourseTagRelations(@Param("courseId") Long courseId);

    void deleteCourseKnowledgePointRelations(@Param("courseId") Long courseId);
    
}
