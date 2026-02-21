package com.sy.course_system.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.course_system.entity.Tag;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {
    
}
