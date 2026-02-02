package com.sy.course_system.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.entity.UserCourseRelation;
import com.sy.course_system.mapper.UserCourseRelationMapper;
import com.sy.course_system.service.UserCourseService;

@Service
public class UserCourseServiceImpl extends ServiceImpl<UserCourseRelationMapper, UserCourseRelation>
        implements UserCourseService {

    @Override
    public int addStudyTimeAndUpdateProgress(Long userId, Long courseId, Integer duration, Integer totalSeconds,
            LocalDateTime now) {
        return baseMapper.addStudyTimeAndUpdateProgress(userId, courseId, duration, totalSeconds, now);
    }

    @Override
    public int tryMarkFinished(Long userId, Long courseId, LocalDateTime now) {
        return baseMapper.tryMarkFinished(userId, courseId, now);
    }

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

    @Override
    public Boolean userAttendCourse(Long courseId) {
        Long userId = UserContext.getUserId();
        // 检查是否已存在关联关系
        LambdaQueryWrapper<UserCourseRelation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserCourseRelation::getUserId, userId)
                .eq(UserCourseRelation::getCourseId, courseId);
        UserCourseRelation existingRelation = this.getOne(queryWrapper);
        if (existingRelation != null) {
            return false; // 已存在关联关系，返回false
        }
        // 创建新的关联关系
        UserCourseRelation newRelation = new UserCourseRelation();
        newRelation.setUserId(userId);
        newRelation.setCourseId(courseId);
        newRelation.setProgress(0);
        newRelation.setLearnedSeconds(0);
        newRelation.setStatus(0); // 未开始
        newRelation.setLastLearnTime(LocalDateTime.now());
        newRelation.setCompleteTime(null);
        newRelation.setIsFavorite(0); // 非收藏
        newRelation.setProgressSeconds(0); // 进度条时长为0

        this.save(newRelation);
        return true;
    }

}
