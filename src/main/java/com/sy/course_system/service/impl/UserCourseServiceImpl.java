package com.sy.course_system.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sy.course_system.entity.UserCourseRelation;
import com.sy.course_system.mapper.UserCourseRelationMapper;
import com.sy.course_system.service.UserCourseService;

@Service
public class UserCourseServiceImpl extends ServiceImpl<UserCourseRelationMapper, UserCourseRelation> implements UserCourseService {
    @Override
    public Integer updateUserCourseRelation(UserCourseRelation relation) {
        return baseMapper.updateUserCourseRelation(relation);
    }

    @Override
    public UserCourseRelation getUserCourseRelation(Long userId, Long courseId) {
        LambdaQueryWrapper<UserCourseRelation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserCourseRelation::getUserId, userId)
                    .eq(UserCourseRelation::getCourseId, courseId);
        return this.getOne(queryWrapper);
    }

    
    
}
