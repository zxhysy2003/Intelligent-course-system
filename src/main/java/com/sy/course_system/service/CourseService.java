package com.sy.course_system.service;

import java.util.List;
import java.util.Map;

import com.sy.course_system.common.PageResult;
import com.sy.course_system.dto.CourseAdminQueryDTO;
import com.sy.course_system.dto.CourseQueryDTO;
import com.sy.course_system.dto.CourseRegisterDTO;
import com.sy.course_system.dto.CourseUpdateDTO;
import com.sy.course_system.entity.Course;
import com.sy.course_system.enums.CourseStatus;
import com.sy.course_system.vo.CourseAdminVO;
import com.sy.course_system.vo.CourseVO;

public interface CourseService {
    // ===== 课程单条查询 =====
    Course getById(Long courseId);
    
    // ===== 课程批量查询 =====
    List<Course> listByIds(List<Long> courseIds);
    
    // ===== 课程信息映射 =====
    Map<Long, Course> mapByIds(List<Long> courseIds);

    // ===== 课程注册 =====
    Integer register(CourseRegisterDTO registerDTO);

    // ===== 课程-知识点 关联查询 =====
    List<Long> getKnowledgePointIdsByCourseId(Long courseId);

    // ===== 前台课程池 =====
    PageResult<CourseVO> pageForUser(CourseQueryDTO queryDTO);

    // ===== 后台课程管理 =====
    PageResult<CourseAdminVO> pageForAdmin(CourseAdminQueryDTO queryDTO);

     // ===== 课程修改 =====
    boolean update(CourseUpdateDTO updateDTO);

     // ===== 课程上下架 =====
    boolean changeStatus(Long courseId, CourseStatus status);

    // ===== 课程删除（逻辑删除）=====
    boolean delete(Long courseId);

    // ===== 课程-知识点 关联绑定 =====
    boolean bindKnowledgePoints(Long courseId, List<Long> knowledgePointIds);

    
    List<CourseVO> listWithQuery(CourseQueryDTO queryDTO);
}
