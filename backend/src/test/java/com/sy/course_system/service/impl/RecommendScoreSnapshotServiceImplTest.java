package com.sy.course_system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sy.course_system.dto.recommend.RecommendScoreSnapshotDTO;
import com.sy.course_system.dto.recommend.UserCourseBaseScoreDTO;
import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.mapper.RecommendScoreSnapshotMapper;
import com.sy.course_system.support.RecommendPropertiesFixture;

@ExtendWith(MockitoExtension.class)
class RecommendScoreSnapshotServiceImplTest {

    @Mock
    private LearningBehaviorMapper learningBehaviorMapper;
    @Mock
    private RecommendScoreSnapshotMapper recommendScoreSnapshotMapper;

    private RecommendScoreSnapshotServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RecommendScoreSnapshotServiceImpl(
                learningBehaviorMapper,
                recommendScoreSnapshotMapper,
                RecommendPropertiesFixture.builder()
                        .scoreSnapshot(snapshot -> snapshot.batchSize(2).rawScoreScale(20.0).minScore(0.1))
                        .build());
    }

    @Test
    void refreshUserCourseScoreShouldUpsertNormalizedSnapshot() {
        when(learningBehaviorMapper.getUserCourseBaseScoreSnapshot(1L, 10L))
                .thenReturn(baseScore(1L, 10L, 20.0));

        service.refreshUserCourseScore(1L, 10L);

        RecommendScoreSnapshotDTO snapshot = captureSingleSnapshot();
        assertEquals(1L, snapshot.getUserId());
        assertEquals(10L, snapshot.getCourseId());
        assertEquals(20.0, snapshot.getRawScore());
        assertEquals(10.0 * (1.0 - Math.exp(-1.0)), snapshot.getScore(), 1e-9);
        verify(recommendScoreSnapshotMapper, never()).deleteByUserCourse(1L, 10L);
    }

    @Test
    void refreshUserCourseScoreShouldDeleteWhenScoreFallsBelowThreshold() {
        when(learningBehaviorMapper.getUserCourseBaseScoreSnapshot(1L, 10L))
                .thenReturn(baseScore(1L, 10L, 0.1));

        service.refreshUserCourseScore(1L, 10L);

        verify(recommendScoreSnapshotMapper).deleteByUserCourse(1L, 10L);
        verify(recommendScoreSnapshotMapper, never()).upsertBatch(anyList());
    }

    @Test
    void refreshUserCourseScoreShouldDeleteWhenNoBaseScoreExists() {
        when(learningBehaviorMapper.getUserCourseBaseScoreSnapshot(1L, 10L)).thenReturn(null);

        service.refreshUserCourseScore(1L, 10L);

        verify(recommendScoreSnapshotMapper).deleteByUserCourse(1L, 10L);
        verify(recommendScoreSnapshotMapper, never()).upsertBatch(anyList());
    }

    @Test
    void rebuildAllScoresShouldReplaceSnapshotsAndSkipLowScores() {
        when(learningBehaviorMapper.listUserCourseBaseScores()).thenReturn(List.of(
                baseScore(1L, 10L, 20.0),
                baseScore(1L, 11L, 0.1),
                baseScore(2L, 12L, 40.0)));

        service.rebuildAllScores();

        verify(recommendScoreSnapshotMapper).deleteAll();
        ArgumentCaptor<List<RecommendScoreSnapshotDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(recommendScoreSnapshotMapper).upsertBatch(captor.capture());
        List<RecommendScoreSnapshotDTO> batch = captor.getValue();
        assertEquals(List.of(10L, 12L), batch.stream().map(RecommendScoreSnapshotDTO::getCourseId).toList());
        assertEquals(20.0, batch.get(0).getRawScore());
        assertEquals(40.0, batch.get(1).getRawScore());
    }

    private RecommendScoreSnapshotDTO captureSingleSnapshot() {
        ArgumentCaptor<List<RecommendScoreSnapshotDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(recommendScoreSnapshotMapper).upsertBatch(captor.capture());
        assertEquals(1, captor.getValue().size());
        return captor.getValue().get(0);
    }

    private UserCourseBaseScoreDTO baseScore(Long userId, Long courseId, Double score) {
        UserCourseBaseScoreDTO dto = new UserCourseBaseScoreDTO();
        dto.setUserId(userId);
        dto.setCourseId(courseId);
        dto.setBaseScore(score);
        return dto;
    }
}
