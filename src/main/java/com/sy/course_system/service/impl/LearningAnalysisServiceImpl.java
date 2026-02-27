package com.sy.course_system.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import com.sy.course_system.dto.AbilityDimensionScoreDTO;
import com.sy.course_system.dto.AbilityRadarDTO;
import com.sy.course_system.dto.ProgressChartDTO;
import com.sy.course_system.dto.ProgressDailyPointDTO;
import com.sy.course_system.dto.ProgressSummaryDTO;
import com.sy.course_system.dto.RadarIndicatorDTO;
import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.mapper.UserCourseRelationMapper;
import com.sy.course_system.repository.KnowledgeRepository;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.vo.CourseVO;

@Service
public class LearningAnalysisServiceImpl implements LearningAnalysisService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private LearningBehaviorMapper learningBehaviorMapper;
    @Autowired
    private UserCourseRelationMapper userCourseRelationMapper;
    @Autowired
    private KnowledgeRepository knowledgeRepository;


    private static final String HOT_COURSE_KEY = "course:hot";
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");


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
        // 2. 回填热度值并排序
        
        for (CourseVO course : courses) {
            course.setHotScore(courseHotMap.getOrDefault(course.getId(), 0.0));
        }

        // 3. 根据热度值对课程进行降序排序
        courses.sort(Comparator.comparing(CourseVO::getHotScore).reversed());
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

    /**
     * 获取用户学习进度图数据（最近 N 天每天的学习时长和活跃课程数，以及总体进度汇总数据）
     */
    @Override
    public ProgressChartDTO getProgressChart(Long userId, Integer days) {
        // 1. 参数校验与默认值
        int safeDays = (days == null || days <= 0) ? 30 : Math.min(days, 180);

        // 2. 从数据库获取用户最近 N 天的学习行为数据（每天的学习时长和活跃课程数）
        List<ProgressDailyPointDTO> dailyRows = learningBehaviorMapper.listDailyProgress(userId, safeDays);
        // 3. 从数据库获取用户的总体学习进度汇总数据（总课程数、已完成课程数、平均进度、总学习时长等）
        ProgressSummaryDTO summary = userCourseRelationMapper.getUserProgressSummary(userId);

        // 4. 构造返回的 DTO 对象
        Map<String, ProgressDailyPointDTO> dayMap = new HashMap<>();
        if (dailyRows != null) {
            for (ProgressDailyPointDTO row : dailyRows) {
                if (row != null && row.getDay() != null) {
                    dayMap.put(row.getDay(), row);
                }
            }
        }

        List<String> dates = new ArrayList<>();
        List<Integer> studySeconds = new ArrayList<>();
        List<Integer> activeCourses = new ArrayList<>();

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(safeDays - 1L);
        for (int i = 0; i < safeDays; i++) {
            String day = start.plusDays(i).format(DAY_FORMATTER);
            dates.add(day);
            ProgressDailyPointDTO row = dayMap.get(day);
            studySeconds.add(row == null || row.getStudySeconds() == null ? 0 : row.getStudySeconds());
            activeCourses.add(row == null || row.getActiveCourses() == null ? 0 : row.getActiveCourses());
        }

        ProgressChartDTO dto = new ProgressChartDTO();
        dto.setDates(dates);
        dto.setStudySeconds(studySeconds);
        dto.setActiveCourses(activeCourses);
        dto.setTotalCourses(summary == null || summary.getTotalCourses() == null ? 0 : summary.getTotalCourses());
        dto.setFinishedCourses(
                summary == null || summary.getFinishedCourses() == null ? 0 : summary.getFinishedCourses());
        dto.setAvgProgress(summary == null || summary.getAvgProgress() == null ? 0.0 : summary.getAvgProgress());
        dto.setTotalLearnedSeconds(summary == null || summary.getTotalLearnedSeconds() == null ? 0
                : summary.getTotalLearnedSeconds());
        return dto;
    }

    @Override
    public AbilityRadarDTO getAbilityRadar(Long userId) {
        List<AbilityDimensionScoreDTO> rows = knowledgeRepository.getAbilityDimensionScores(userId);
        if (rows == null) {
            rows = List.of();
        }

        List<RadarIndicatorDTO> indicators = rows.stream()
                .map(r -> new RadarIndicatorDTO(r.getDimension(), 100))
                .toList();
        List<Double> values = rows.stream()
                .map(r -> {
                    double v = r.getValue() == null ? 0.0 : r.getValue();
                    if (v < 0.0) {
                        return 0.0;
                    }
                    if (v > 100.0) {
                        return 100.0;
                    }
                    return v;
                })
                .toList();

        AbilityRadarDTO dto = new AbilityRadarDTO();
        dto.setIndicator(indicators);
        dto.setValues(values);
        return dto;
    }


    
}
