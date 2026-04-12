package com.sy.course_system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.course_system.entity.UserOnboardingProfile;

@Mapper
public interface UserOnboardingProfileMapper extends BaseMapper<UserOnboardingProfile> {

    UserOnboardingProfile selectByUserId(@Param("userId") Long userId);
}
