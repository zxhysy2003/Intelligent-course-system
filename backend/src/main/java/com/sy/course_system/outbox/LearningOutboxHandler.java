package com.sy.course_system.outbox;

import com.sy.course_system.mapper.UserCourseRelationMapper;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.recommend.RecommendCacheInvalidator;
import com.sy.course_system.service.RecommendScoreSnapshotService;

@Component
public class LearningOutboxHandler {
    private final UserCourseRelationMapper relations;
    private final ObjectMapper json;
    private final LearningHotUpdater hot;
    private final LearningMasteryUpdater mastery;
    private final RecommendScoreSnapshotService snapshots;
    private final RecommendCacheInvalidator invalidator;
    public LearningOutboxHandler(UserCourseRelationMapper relations, ObjectMapper json, LearningHotUpdater hot,
            LearningMasteryUpdater mastery, RecommendScoreSnapshotService snapshots, RecommendCacheInvalidator invalidator) {
        this.relations = relations;
        this.json = json;
        this.hot = hot;
        this.mastery = mastery;
        this.snapshots = snapshots;
        this.invalidator = invalidator;
    }

    /** 返回 true 表示源业务已删除或课程下线导致任务被跳过。 */
    public boolean handle(LearningOutboxTask task) throws Exception {
        LearningOutboxPayload payload = json.readValue(task.payload(), LearningOutboxPayload.class);
        if (payload.version() != 1) throw new IllegalArgumentException("不支持的学习事件版本");
        Integer status = relations.selectCourseStatusForActiveRelation(task.userId(), task.courseId());
        if (status == null) return true;
        switch (task.type()) {
            case "HOT_INCREMENT" -> {
                if (!Integer.valueOf(1).equals(status)) return true;
                hot.increment(task.id(), task.courseId(), payload.hotDelta());
            }
            case "MASTERY_UPDATE" -> {
                if (payload.mastery() == null || !Double.isFinite(payload.mastery())
                        || payload.mastery() < 0 || payload.mastery() > 1 || payload.finishedAt() == null
                        || payload.knowledgePointIds() == null || payload.knowledgePointIds().isEmpty()) {
                    throw new IllegalArgumentException("无效掌握度载荷");
                }
                mastery.update(task.userId(), payload);
            }
            case "SCORE_REFRESH" -> snapshots.refreshUserCourseScore(task.userId(), task.courseId());
            case "RECOMMEND_INVALIDATE" -> invalidator.invalidateFromOutbox(task.userId(), payload.invalidationMode());
            default -> throw new IllegalArgumentException("未知学习任务类型: " + task.type());
        }
        return false;
    }
}
