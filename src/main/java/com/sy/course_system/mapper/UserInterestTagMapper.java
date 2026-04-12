package com.sy.course_system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.course_system.entity.UserInterestTag;

@Mapper
public interface UserInterestTagMapper extends BaseMapper<UserInterestTag> {

    int deleteByUserIdAndSource(@Param("userId") Long userId, @Param("source") String source);

    int batchInsert(@Param("rows") List<UserInterestTag> rows);

    List<Long> selectTagIdsByUserIdAndSource(@Param("userId") Long userId, @Param("source") String source);
}
