package com.sy.course_system.service;

import java.time.LocalDateTime;
import java.util.List;

import com.sy.course_system.entity.UserCourseRelation;

public interface UserCourseService {
    // 增加学习时长并更新进度
    int addStudyTimeAndUpdateProgress(Long userId, Long courseId, Integer duration, Integer totalSeconds, java.time.LocalDateTime now);
    // 尝试标记课程为已完成
    int tryMarkFinished(Long userId, Long courseId, LocalDateTime now);
    // 更新用户课程关联关系
    Integer updateUserCourseRelation(UserCourseRelation relation);
    // 查询用户课程关联关系
    UserCourseRelation getUserCourseRelation(Long userId, Long courseId);
    // 用户选修课程
    Boolean userAttendCourse(Long courseId);

    // 从候选课程中查询当前用户已选课程 ID 列表，用于推荐过滤
    List<Long> listSelectedCourseIds(Long userId, List<Long> courseIds);
}
