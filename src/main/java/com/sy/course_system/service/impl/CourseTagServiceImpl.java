package com.sy.course_system.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sy.course_system.entity.Tag;
import com.sy.course_system.mapper.TagMapper;
import com.sy.course_system.service.CourseTagService;

@Service
public class CourseTagServiceImpl extends ServiceImpl<TagMapper, Tag> implements CourseTagService {

    @Override
    public Map<Integer, Tag> getTagMapByIds(List<Integer> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return null;
        }
        List<Tag> tags = baseMapper.selectBatchIds(tagIds);
        Map<Integer, Tag> tagMap = tags.stream().collect(Collectors.toMap(Tag::getId, tag -> tag));
        return tagMap;
    }

}
