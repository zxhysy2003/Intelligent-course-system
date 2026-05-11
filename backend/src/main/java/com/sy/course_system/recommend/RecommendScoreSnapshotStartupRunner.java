package com.sy.course_system.recommend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.service.RecommendScoreSnapshotService;

@Component
public class RecommendScoreSnapshotStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecommendScoreSnapshotStartupRunner.class);

    private final RecommendProperties recommendProperties;
    private final RecommendScoreSnapshotService recommendScoreSnapshotService;

    public RecommendScoreSnapshotStartupRunner(RecommendProperties recommendProperties,
            RecommendScoreSnapshotService recommendScoreSnapshotService) {
        this.recommendProperties = recommendProperties;
        this.recommendScoreSnapshotService = recommendScoreSnapshotService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!recommendProperties.scoreSnapshot().rebuildOnStartup()) {
            return;
        }
        try {
            recommendScoreSnapshotService.rebuildAllScores();
        } catch (RuntimeException e) {
            log.warn("Failed to rebuild recommend score snapshots on startup", e);
        }
    }
}
