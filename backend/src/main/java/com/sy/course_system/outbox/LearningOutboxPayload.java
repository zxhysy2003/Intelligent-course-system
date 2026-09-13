package com.sy.course_system.outbox;

import java.time.LocalDateTime;
import java.util.List;

/** 载荷保存事件发生时的固定输入，重试不得重新生成增量或完课分数。 */
public record LearningOutboxPayload(int version, double hotDelta, List<Long> knowledgePointIds,
        Double mastery, LocalDateTime finishedAt, String invalidationMode) {
    public static LearningOutboxPayload of(double hotDelta, List<Long> ids, Double mastery,
            LocalDateTime finishedAt, String mode) {
        return new LearningOutboxPayload(1, hotDelta, ids == null ? List.of() : List.copyOf(ids),
                mastery, finishedAt, mode);
    }
}
