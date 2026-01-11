package com.sy.course_system.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.sy.course_system.common.UserContext;
import com.sy.course_system.entity.Course;
import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.vo.CourseRecommendVO;

@Service
public class LearningAnalysisServiceImpl implements LearningAnalysisService {

    @Autowired
    private LearningBehaviorMapper learningBehaviorMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CourseService courseService;

    private static final String HOT_COURSE_KEY = "course:hot";

    @Override
    public Integer getMyTotalStudyTime() {
        Long userId = UserContext.getUserId();
        return learningBehaviorMapper.sumStudyDurationByUser(userId);
    }

    @Override
    public List<Long> getMyLearnedCourses() {
        Long userId = UserContext.getUserId();
        return learningBehaviorMapper.selectLearnedCourseIds(userId);
    }


    /**
     * 增加课程热度
     * @param courseId 课程ID
     * @param score 热度分值（可按学习行为加权）
     */
    @Override
    public void increaseCourseHot(Long courseId, double score) {
        redisTemplate.opsForZSet().incrementScore(HOT_COURSE_KEY, courseId, score);
    }

    /**
    * 获取热门课程 TopN
    */
    @Override
    public List<CourseRecommendVO> getHotCourses(Integer topN) {
        // 1.从redis sorted set中获取热度最高的 TopN 个课程ID
        Set<Object> courseIdSet = redisTemplate.opsForZSet()
                .reverseRange(HOT_COURSE_KEY, 0, topN - 1);
        if (courseIdSet == null || courseIdSet.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> courseIds = courseIdSet.stream()
                .map(id -> Long.parseLong(id.toString()))
                .collect(Collectors.toList());
        // 2.调用课程服务获取课程详情
        Map<Long, Course> courseMap = courseService.mapByIds(courseIds);

        // 3.构建返回结果，保持热度排序
        List<CourseRecommendVO> result = new ArrayList<>();
        for (Long courseId : courseIds) {
            Course course = courseMap.get(courseId);
            if (course == null) continue;
            CourseRecommendVO vo = new CourseRecommendVO();
            vo.setCourseId(course.getId());
            vo.setTitle(course.getTitle());
            vo.setCoverUrl(course.getCoverUrl());
            vo.setDifficulty(course.getDifficulty());
            vo.setDuration(course.getDuration());
            vo.setTags(parseTags(course.getTags()));
            vo.setRecommendReason("热门课程");
            result.add(vo);
        }
        return result;
    }

    private List<String> parseTags(String tagsStr) {
        if (tagsStr == null || tagsStr.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tagsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public void refreshUserRecommendCache(Long userId) {
        String key = "recommend:user:" + userId;
        redisTemplate.delete(key); // 删除缓存，下次访问时会重新计算推荐结果
    }


    
}
