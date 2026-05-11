package com.sy.course_system.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sy.course_system.common.util.TimeDecayUtil;
import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.recommend.RecommendScoreSnapshotDTO;
import com.sy.course_system.dto.recommend.UserCourseBaseScoreDTO;
import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.mapper.RecommendScoreSnapshotMapper;
import com.sy.course_system.service.RecommendScoreSnapshotService;

@Service
public class RecommendScoreSnapshotServiceImpl implements RecommendScoreSnapshotService {

    private final LearningBehaviorMapper learningBehaviorMapper;
    private final RecommendScoreSnapshotMapper recommendScoreSnapshotMapper;
    private final RecommendProperties recommendProperties;

    public RecommendScoreSnapshotServiceImpl(LearningBehaviorMapper learningBehaviorMapper,
            RecommendScoreSnapshotMapper recommendScoreSnapshotMapper,
            RecommendProperties recommendProperties) {
        this.learningBehaviorMapper = learningBehaviorMapper;
        this.recommendScoreSnapshotMapper = recommendScoreSnapshotMapper;
        this.recommendProperties = recommendProperties;
    }

    @Override
    @Transactional(transactionManager = "transactionManager", propagation = Propagation.REQUIRES_NEW)
    public void refreshUserCourseScore(Long userId, Long courseId) {
        if (userId == null || courseId == null) {
            return;
        }
        UserCourseBaseScoreDTO baseScore = learningBehaviorMapper.getUserCourseBaseScoreSnapshot(userId, courseId);
        RecommendScoreSnapshotDTO snapshot = toSnapshot(baseScore);
        if (snapshot == null) {
            recommendScoreSnapshotMapper.deleteByUserCourse(userId, courseId);
            return;
        }
        recommendScoreSnapshotMapper.upsertBatch(List.of(snapshot));
    }

    @Override
    @Transactional(transactionManager = "transactionManager")
    public void rebuildAllScores() {
        List<UserCourseBaseScoreDTO> baseScores = learningBehaviorMapper.listUserCourseBaseScores();
        recommendScoreSnapshotMapper.deleteAll();
        if (baseScores == null || baseScores.isEmpty()) {
            return;
        }

        List<RecommendScoreSnapshotDTO> batch = new ArrayList<>();
        int batchSize = Math.max(recommendProperties.scoreSnapshot().batchSize(), 1);
        for (UserCourseBaseScoreDTO baseScore : baseScores) {
            RecommendScoreSnapshotDTO snapshot = toSnapshot(baseScore);
            if (snapshot == null) {
                continue;
            }
            batch.add(snapshot);
            if (batch.size() >= batchSize) {
                recommendScoreSnapshotMapper.upsertBatch(batch);
                batch = new ArrayList<>();
            }
        }
        if (!batch.isEmpty()) {
            recommendScoreSnapshotMapper.upsertBatch(batch);
        }
    }

    private RecommendScoreSnapshotDTO toSnapshot(UserCourseBaseScoreDTO baseScore) {
        if (baseScore == null || baseScore.getUserId() == null || baseScore.getCourseId() == null) {
            return null;
        }
        double base = safeScore(baseScore.getBaseScore());
        double rawScore = base * TimeDecayUtil.decay(baseScore.getLastTime());
        double score = normalize(rawScore);
        if (score < recommendProperties.scoreSnapshot().minScore()) {
            return null;
        }
        return new RecommendScoreSnapshotDTO(
                baseScore.getUserId(),
                baseScore.getCourseId(),
                rawScore,
                score,
                baseScore.getLastTime());
    }

    private double normalize(double rawScore) {
        if (!Double.isFinite(rawScore) || rawScore <= 0.0) {
            return 0.0;
        }
        double scale = Math.max(recommendProperties.scoreSnapshot().rawScoreScale(), 1e-9);
        return 10.0 * (1.0 - Math.exp(-rawScore / scale));
    }

    private double safeScore(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0.0) {
            return 0.0;
        }
        return value;
    }
}
