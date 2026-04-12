package com.sy.course_system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.course_system.dto.course.TagOptionDTO;
import com.sy.course_system.entity.Tag;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    @Select("""
            SELECT id, name, type
            FROM tag
            WHERE status = 1
            ORDER BY type, name
            """)
    List<TagOptionDTO> listEnabledTagOptions();

    List<TagOptionDTO> listEnabledTagOptionsByIds(@Param("tagIds") List<Long> tagIds);

    List<TagOptionDTO> listEnabledOnboardingTagOptions();

    List<Tag> selectOnboardingTagsByIds(@Param("tagIds") List<Long> tagIds);
}
