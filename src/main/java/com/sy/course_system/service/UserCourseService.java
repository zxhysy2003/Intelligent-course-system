package com.sy.course_system.service;

import com.sy.course_system.entity.UserCourseRelation;

public interface UserCourseService {
    // 更新用户课程关联关系
    Integer updateUserCourseRelation(UserCourseRelation relation);
    // 查询用户课程关联关系
    UserCourseRelation getUserCourseRelation(Long userId, Long courseId);
}
