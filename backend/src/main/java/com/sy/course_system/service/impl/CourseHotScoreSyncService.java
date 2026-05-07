package com.sy.course_system.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.course.CourseHotScoreDTO;
import com.sy.course_system.mapper.CourseHotScoreMapper;

@Service
public class CourseHotScoreSyncService {
    private static final Logger log = LoggerFactory.getLogger(CourseHotScoreSyncService.class);
    private static final String HOT_COURSE_KEY = "course:hot";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CourseHotScoreMapper courseHotScoreMapper;

    @Autowired
    private RecommendProperties recommendProperties;

    @Scheduled(fixedDelayString = "#{@recommendProperties.hotSync.fixedDelayMs}")
    public void syncOnce() {
        if (!recommendProperties.getHotSync().isEnabled()) {
            return;
        }
        try {
            List<CourseHotScoreDTO> hotScores = loadHotScoresFromRedis();
            replaceHotScoreSnapshots(hotScores);
        } catch (RuntimeException ex) {
            log.warn("同步课程热度快照失败", ex);
        }
    }

    private List<CourseHotScoreDTO> loadHotScoresFromRedis() {
        int safeBatchSize = Math.max(recommendProperties.getHotSync().getBatchSize(), 1);
        List<CourseHotScoreDTO> hotScores = new ArrayList<>();
        int start = 0;
        while (true) {
            Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(HOT_COURSE_KEY, start, start + safeBatchSize - 1L);
            if (tuples == null || tuples.isEmpty()) {
                break;
            }
            for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
                CourseHotScoreDTO dto = toHotScoreDTO(tuple);
                if (dto != null) {
                    hotScores.add(dto);
                }
            }
            if (tuples.size() < safeBatchSize) {
                break;
            }
            start += safeBatchSize;
        }
        return hotScores;
    }

    private CourseHotScoreDTO toHotScoreDTO(ZSetOperations.TypedTuple<Object> tuple) {
        if (tuple == null || tuple.getValue() == null || tuple.getScore() == null) {
            return null;
        }
        Long courseId = parseCourseId(tuple.getValue());
        if (courseId == null) {
            return null;
        }
        return new CourseHotScoreDTO(courseId, tuple.getScore());
    }

    private Long parseCourseId(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            log.warn("忽略无法解析的课程热度 member: {}", value);
            return null;
        }
    }

    private void replaceHotScoreSnapshots(List<CourseHotScoreDTO> hotScores) {
        if (hotScores == null || hotScores.isEmpty()) {
            courseHotScoreMapper.deleteAllHotScores();
            return;
        }
        int safeBatchSize = Math.max(recommendProperties.getHotSync().getBatchSize(), 1);
        for (int start = 0; start < hotScores.size(); start += safeBatchSize) {
            int end = Math.min(start + safeBatchSize, hotScores.size());
            courseHotScoreMapper.upsertHotScores(hotScores.subList(start, end));
        }
        List<Long> courseIds = hotScores.stream()
                .map(CourseHotScoreDTO::getCourseId)
                .distinct()
                .toList();
        courseHotScoreMapper.deleteHotScoresNotIn(courseIds);
    }
}
