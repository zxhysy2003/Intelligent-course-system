package com.sy.course_system.service;

import java.util.List;
import java.util.Map;

import com.sy.course_system.entity.Tag;

public interface CourseTagService {
    
     List<Tag> getTagByIds (List<Integer> tagIds);

     Map<Integer, Tag> getTagMapByIds(List<Integer> tagIds);
}
