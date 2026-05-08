package com.sy.course_system.service.impl;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.course.CourseHotScoreDTO;
import com.sy.course_system.mapper.CourseHotScoreMapper;
import com.sy.course_system.support.RecommendPropertiesFixture;

@ExtendWith(MockitoExtension.class)
class CourseHotScoreSyncServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ZSetOperations<String, Object> zSetOperations;
    @Mock
    private CourseHotScoreMapper courseHotScoreMapper;

    private CourseHotScoreSyncService syncService;

    private RecommendProperties recommendProperties;

    @BeforeEach
    void setUp() {
        recommendProperties = RecommendPropertiesFixture.builder()
                .hotSync(hotSync -> hotSync.enabled(true).batchSize(2))
                .build();
        syncService = new CourseHotScoreSyncService(redisTemplate, courseHotScoreMapper, recommendProperties);
    }

    @Test
    void syncOnceShouldUpsertRedisHotScoresAndDeleteMissingSnapshots() {
        Set<ZSetOperations.TypedTuple<Object>> firstBatch = new LinkedHashSet<>();
        firstBatch.add(new DefaultTypedTuple<>((Object) 1L, 8.0));
        firstBatch.add(new DefaultTypedTuple<>((Object) "2", 3.5));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeWithScores("course:hot", 0, 1)).thenReturn(firstBatch);
        when(zSetOperations.reverseRangeWithScores("course:hot", 2, 3)).thenReturn(Set.of());

        syncService.syncOnce();

        verify(courseHotScoreMapper).upsertHotScores(argThat(rows -> hasHotScore(rows, 1L, 8.0)
                && hasHotScore(rows, 2L, 3.5)));
        verify(courseHotScoreMapper).deleteHotScoresNotIn(List.of(1L, 2L));
    }

    @Test
    void syncOnceShouldClearSnapshotsWhenRedisHotSetIsEmpty() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRangeWithScores("course:hot", 0, 1)).thenReturn(Set.of());

        syncService.syncOnce();

        verify(courseHotScoreMapper).deleteAllHotScores();
        verify(courseHotScoreMapper, never()).upsertHotScores(argThat(rows -> true));
    }

    @Test
    void syncOnceShouldIgnoreRedisFailure() {
        when(redisTemplate.opsForZSet()).thenThrow(new RuntimeException("redis unavailable"));

        syncService.syncOnce();

        verifyNoInteractions(courseHotScoreMapper);
    }

    private boolean hasHotScore(List<CourseHotScoreDTO> rows, Long courseId, Double score) {
        return rows.stream()
                .anyMatch(row -> courseId.equals(row.getCourseId()) && score.equals(row.getHotScore()));
    }
}
