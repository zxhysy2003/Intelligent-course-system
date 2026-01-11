package com.sy.course_system.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.dto.RecommendRequestDTO;
import com.sy.course_system.dto.RecommendResponseDTO;
import com.sy.course_system.dto.UserCourseScoreDTO;
import com.sy.course_system.entity.Course;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.LearningBehaviorService;
import com.sy.course_system.service.RecommendService;
import com.sy.course_system.vo.CourseRecommendVO;

@Service
public class RecommendServiceImpl implements RecommendService {
    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LearningBehaviorService learningBehaviorService;

    @Autowired
    private CourseService courseService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${recommend.service.url}")
    private String recommendServiceUrl;

    private static final long CACHE_TTL_MINUTES = 15; // 缓存15分钟

    public RecommendResponseDTO recommend(Long userId) {

        // // 1. 获取所有用户的学习行为数据
        // List<LearningBehavior> behaviors = learningBehaviorService.listAllBehaviors();

        // // 2. 聚合成 (userId, courseId, score) 格式的数据
        // Map<String, Double> scoresMap = ScoreAggregateService.aggregate(behaviors);

        // List<UserCourseScoreDTO> scoreList = ScoreAggregateService.toScoreDTO(scoresMap);

        List<UserCourseScoreDTO> scoreList = learningBehaviorService.listAggregatedScores();

        // 3. 调用推荐服务
        RecommendRequestDTO request = new RecommendRequestDTO();
        request.setTargetUserId(userId);
        request.setData(scoreList);
        request.setTopN(5); // 假设推荐前5个课程

        return restTemplate.postForObject(
                recommendServiceUrl + "/recommend",
                request,
                RecommendResponseDTO.class);
    }

    public List<CourseRecommendVO> recommendCourseVO(Long userId) {

        // 1.尝试从Redis缓存获取推荐结果
        String key = "recommend:user:" + userId;
        Object cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            List<CourseRecommendVO> cache = new ObjectMapper().convertValue(cached, new TypeReference<List<CourseRecommendVO>>() {});
            return cache;
        }
        
        // 2.缓存未命中 -> 调用原来的推荐逻辑

        RecommendResponseDTO response = recommend(userId);

        List<Long> courseIds = response.getCourseIds();
        if (courseIds == null || courseIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查课程 -> Map
        Map<Long, Course> courseMap = courseService.mapByIds(courseIds);

        // 按推荐顺序组装VO列表
        List<CourseRecommendVO> result = new ArrayList<>();

        for (Long courseId : courseIds) {
            Course course = courseMap.get(courseId);
            if (course == null) {
                continue; // 课程下线或不存在
            }
            CourseRecommendVO vo = new CourseRecommendVO();
            vo.setCourseId(course.getId());
            vo.setTitle(course.getTitle());
            vo.setCoverUrl(course.getCoverUrl());
            vo.setDifficulty(course.getDifficulty());
            vo.setDuration(course.getDuration());
            vo.setTags(parseTags(course.getTags()));

            // 设置推荐理由(规则生成)
            vo.setRecommendReason(buildRecommendReason(userId, course));
            result.add(vo);
        }

        // 3.将结果缓存到Redis
        redisTemplate.opsForValue().set(key, result, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

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

    private String buildRecommendReason(Long userId, Course course) {
        // 简单示例：根据课程难度生成推荐理由
        switch (course.getDifficulty()) {
            case 1:
                return "适合初学者的入门课程";
            case 2:
                return "提升技能的进阶课程";
            case 3:
                return "挑战自我的高级课程";
            default:
                return "优质推荐课程";
        }
    }

    
}
