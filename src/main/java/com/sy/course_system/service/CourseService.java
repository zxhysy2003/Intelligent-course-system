package com.sy.course_system.service;

import java.util.List;
import java.util.Map;

import com.sy.course_system.common.PageResult;
import com.sy.course_system.dto.course.CourseAdminQueryDTO;
import com.sy.course_system.dto.course.CourseQueryDTO;
import com.sy.course_system.dto.course.CourseRegisterDTO;
import com.sy.course_system.dto.course.CourseUpdateDTO;
import com.sy.course_system.enums.CourseStatus;
import com.sy.course_system.vo.CourseAdminVO;
import com.sy.course_system.vo.CourseDetailVO;
import com.sy.course_system.vo.CourseVO;
import com.sy.course_system.vo.KnowledgeVO;

public interface CourseService {
    // ===== 课程注册 =====
    Integer register(CourseRegisterDTO registerDTO);

    // ===== 课程-知识点 关联查询 =====
    List<Long> getKnowledgePointIdsByCourseId(Long courseId);

    // ===== 根据课程id列表获取课程知识点列表 =====
    List<KnowledgeVO> getKnowledgePointsByCourseId(Long courseId);

    // ===== 根据知识点id获取课程详情集合 =====
    List<CourseDetailVO> getCourseDetailsByKnowledgePointId(Long kpId);

    // ===== 前台课程池 =====
    PageResult<CourseVO> pageForUser(CourseQueryDTO queryDTO);

    // ===== 获取课程视频地址 =====
    String getCourseVideoPath(Long courseId);

    // ===== 根据课程id获取课程详情 =====
    CourseDetailVO getCourseByIdForUser(Long courseId);

    // ===== 根据课程id列表获取课程标题列表 =====
    Map<Long, String> getCourseTitleMapByIds(List<Long> courseIds);

    // ===============================================================

    // ===== 后台课程管理 =====
    PageResult<CourseAdminVO> pageForAdmin(CourseAdminQueryDTO queryDTO);

     // ===== 课程修改 =====
    boolean update(CourseUpdateDTO updateDTO);

     // ===== 课程上下架 =====
    boolean changeStatus(Long courseId, CourseStatus status);

    // ===== 课程删除（逻辑删除）=====
    boolean removeCourses(List<Long> courseIds);

    // ===== 课程-知识点 关联绑定 =====
    boolean bindKnowledgePoints(Long courseId, List<Long> knowledgePointIds);

}
