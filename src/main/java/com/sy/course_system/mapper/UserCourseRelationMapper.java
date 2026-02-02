package com.sy.course_system.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.course_system.entity.UserCourseRelation;

import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserCourseRelationMapper extends BaseMapper<UserCourseRelation> {

    int updateUserCourseRelation(@Param("relation") UserCourseRelation relation);

    int addStudyTimeAndUpdateProgress(@Param("userId") Long userId,
            @Param("courseId") Long courseId,
            @Param("duration") int duration,
            @Param("totalSeconds") int totalSeconds,
            @Param("now") LocalDateTime now);

    int tryMarkFinished(@Param("userId") Long userId,
            @Param("courseId") Long courseId,
            @Param("now") LocalDateTime now);

}
