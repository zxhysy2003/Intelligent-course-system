package com.sy.course_system.service;

import java.util.List;
import java.util.Map;

import com.sy.course_system.entity.Tag;

public interface CourseTagService {

    Map<Integer, Tag> getTagMapByIds(List<Integer> tagIds);
}
