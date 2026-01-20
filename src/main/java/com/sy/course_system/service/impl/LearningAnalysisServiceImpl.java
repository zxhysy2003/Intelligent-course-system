package com.sy.course_system.service.impl;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.entity.Course;
import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.vo.CourseVO;

@Service
public class LearningAnalysisServiceImpl implements LearningAnalysisService {

    @Autowired
    private LearningBehaviorMapper learningBehaviorMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


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
    public List<Long> getHotCourses(Integer topN) {
        // 1.从redis sorted set中获取热度最高的 TopN 个课程ID
        Set<Object> courseIdSet = redisTemplate.opsForZSet()
                .reverseRange(HOT_COURSE_KEY, 0, topN - 1);
        if (courseIdSet == null || courseIdSet.isEmpty()) {
            return Collections.emptyList();
        }

        return courseIdSet.stream()
                .map(id -> Long.parseLong(id.toString()))
                .toList();
    }

    @Override
    public void refreshUserRecommendCache(Long userId) {
        String key = "recommend:user:" + userId;
        redisTemplate.delete(key); // 删除缓存，下次访问时会重新计算推荐结果
    }

    @Override
    public List<CourseVO> sortCoursesByHotness(List<CourseVO> courses) {
        if (courses == null || courses.isEmpty()) {
            return courses;
        }
        // 1. 批量获取所有课程的热度值
        Map<Long, Double> courseHotMap = batchGetCourseHotness(courses);
        if (CollectionUtils.isEmpty(courseHotMap)) {
            return courses;
        }

        // 2. 根据热度值对课程进行降序排序
        courses.sort(Comparator.comparingDouble(
            course -> -courseHotMap.getOrDefault(course.getId(), 0.0)
        ));
        return courses;
    }

    private Map<Long, Double> batchGetCourseHotness(List<CourseVO> courses) {
        // 提取课程ID列表
        List<Long> courseIds = courses.stream()
                .map(CourseVO::getId)
                .collect(Collectors.toList());
        
        // 批量获取热度值（使用 SessionCallback 与 opsForZSet().score）
        List<Object> results = stringRedisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (Long courseId : courseIds) {
                    operations.opsForZSet().score(HOT_COURSE_KEY, courseId.toString());
                }
                return null;
            }
        });

        Map<Long, Double> map = new HashMap<>();
        if (results != null) {
            for (int i = 0; i < courseIds.size(); i++) {
                Object result = results.get(i);
                Double score = (result == null) ? 0.0 : Double.valueOf(result.toString());
                map.put(courseIds.get(i), score);
            }
        }
        return map;
    }


    
}
