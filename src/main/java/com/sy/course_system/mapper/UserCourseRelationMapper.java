package com.sy.course_system.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.course_system.entity.UserCourseRelation;

import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserCourseRelationMapper extends BaseMapper<UserCourseRelation> {

    int updateUserCourseRelation(@Param("relation") UserCourseRelation relation);
}
