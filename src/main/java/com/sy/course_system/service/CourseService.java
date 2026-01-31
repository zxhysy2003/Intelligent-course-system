package com.sy.course_system.service;

import java.util.List;
import com.sy.course_system.common.PageResult;
import com.sy.course_system.dto.CourseAdminQueryDTO;
import com.sy.course_system.dto.CourseQueryDTO;
import com.sy.course_system.dto.CourseRegisterDTO;
import com.sy.course_system.dto.CourseUpdateDTO;
import com.sy.course_system.enums.CourseStatus;
import com.sy.course_system.vo.CourseAdminVO;
import com.sy.course_system.vo.CourseDetailVO;
import com.sy.course_system.vo.CourseVO;

public interface CourseService {
    // ===== 课程注册 =====
    Integer register(CourseRegisterDTO registerDTO);

    // ===== 课程-知识点 关联查询 =====
    List<Long> getKnowledgePointIdsByCourseId(Long courseId);

    // ===== 前台课程池 =====
    PageResult<CourseVO> pageForUser(CourseQueryDTO queryDTO);

    // ===== 获取课程视频地址 =====
    String getCourseVideoPath(Long courseId);

    // ===== 根据课程id获取课程详情 =====
    CourseDetailVO getCourseByIdForUser(Long courseId);

    // ===============================================================

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

}
