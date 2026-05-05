package com.sy.course_system.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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

@Service
public class LearningAnalysisServiceImpl implements LearningAnalysisService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private LearningBehaviorMapper learningBehaviorMapper;
    @Autowired
    private UserCourseRelationMapper userCourseRelationMapper;
    @Autowired
    private KnowledgeRepository knowledgeRepository;

    private static final String HOT_COURSE_KEY = "course:hot";
    private static final String SCORE_MATRIX_CACHE_KEY = "recommend:score-matrix";
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 增加课程热度
     * 
     * @param courseId 课程ID
     * @param score    热度分值（可按学习行为加权）
     */
    @Override
    public void increaseCourseHot(Long courseId, double score) {
        redisTemplate.opsForZSet().incrementScore(HOT_COURSE_KEY, courseId, score);
    }

    @Override
    public void removeCourseHot(Long courseId) {
        if (courseId == null) {
            return;
        }
        // 这里只做“尽力清理”：
        // - Redis 热榜属于推荐侧附属索引，不应反向决定主业务成败；
        // - 读取链路仍会在 SQL 侧再次校验课程状态，避免 Redis 中的残留脏数据直接暴露给用户。
        redisTemplate.opsForZSet().remove(HOT_COURSE_KEY, courseId);
    }

    @Override
    public void removeCourseHotBatch(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return;
        }
        Object[] members = courseIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toArray();
        if (members.length == 0) {
            return;
        }
        redisTemplate.opsForZSet().remove(HOT_COURSE_KEY, members);
    }

    /**
     * 按热度区间读取课程 ID。
     *
     * 该方法主要服务于“热门兜底扫描补满”策略：调用方会分批向后扫描 Redis 热榜，
     * 这样即使前面的 hot ids 中夹杂已下线课程，也还能继续拿到后面的在线热门课。
     */
    @Override
    public List<Long> getHotCoursesByRange(int startInclusive, int limit) {
        if (startInclusive < 0 || limit <= 0) {
            return Collections.emptyList();
        }
        Set<Object> courseIdSet = redisTemplate.opsForZSet()
                .reverseRange(HOT_COURSE_KEY, startInclusive, startInclusive + limit - 1L);
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
        redisTemplate.delete(SCORE_MATRIX_CACHE_KEY);
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
