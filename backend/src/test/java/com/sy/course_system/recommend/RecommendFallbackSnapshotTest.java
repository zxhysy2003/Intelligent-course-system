package com.sy.course_system.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;

class RecommendFallbackSnapshotTest {

    @Test
    void failedRefreshShouldKeepLastSuccessfulSnapshot() {
        HotFallbackRecommendService hotFallback = mock(HotFallbackRecommendService.class);
        RecommendScoreNormalizer normalizer = mock(RecommendScoreNormalizer.class);
        RecommendFallbackSnapshot snapshot = new RecommendFallbackSnapshot(hotFallback, normalizer);
        HybridRecommendItemDTO item = new HybridRecommendItemDTO();
        item.setCourseId(10L);
        when(hotFallback.buildHotFallbackItems()).thenReturn(List.of(item));

        snapshot.refresh();
        doThrow(new RuntimeException("database unavailable")).when(hotFallback).buildHotFallbackItems();
        snapshot.refresh();

        assertEquals(7L, snapshot.get(7L).getUserId());
        assertEquals(List.of(10L), snapshot.get(7L).getItems().stream()
                .map(HybridRecommendItemDTO::getCourseId).toList());
    }

    @Test
    void missingSnapshotShouldReturnLegalEmptyResponse() {
        RecommendFallbackSnapshot snapshot = new RecommendFallbackSnapshot(
                mock(HotFallbackRecommendService.class), mock(RecommendScoreNormalizer.class));

        assertEquals(8L, snapshot.get(8L).getUserId());
        assertTrue(snapshot.get(8L).getItems().isEmpty());
    }
}
