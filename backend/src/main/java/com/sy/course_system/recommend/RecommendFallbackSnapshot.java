package com.sy.course_system.recommend;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;

/**
 * 预计算热门推荐快照。请求降级路径只读取内存，不访问 Redis、MySQL、Neo4j 或 CF。
 */
@Component
public class RecommendFallbackSnapshot {

    private static final Logger log = LoggerFactory.getLogger(RecommendFallbackSnapshot.class);

    private final HotFallbackRecommendService hotFallbackRecommendService;
    private final RecommendScoreNormalizer scoreNormalizer;
    private final AtomicReference<List<HybridRecommendItemDTO>> snapshot = new AtomicReference<>(List.of());

    public RecommendFallbackSnapshot(HotFallbackRecommendService hotFallbackRecommendService,
            RecommendScoreNormalizer scoreNormalizer) {
        this.hotFallbackRecommendService = hotFallbackRecommendService;
        this.scoreNormalizer = scoreNormalizer;
    }

    @Scheduled(fixedDelayString = "${recommend.cache.fallback-refresh-millis:300000}",
            initialDelayString = "${recommend.cache.fallback-initial-delay-millis:0}")
    public void refresh() {
        try {
            HybridRecommendResponseDTO response = new HybridRecommendResponseDTO(null,
                    hotFallbackRecommendService.buildHotFallbackItems());
            scoreNormalizer.fillRecommendScores(response);
            snapshot.set(response.getItems() == null ? List.of() : List.copyOf(response.getItems()));
        } catch (RuntimeException ex) {
            log.warn("Failed to refresh recommend fallback snapshot, keeping last successful value", ex);
        }
    }

    public HybridRecommendResponseDTO get(Long userId) {
        return new HybridRecommendResponseDTO(userId, snapshot.get());
    }
}
