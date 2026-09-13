package com.sy.course_system.outbox;

import java.util.UUID;
import com.sy.course_system.mapper.LearningOutboxMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LearningOutboxWriter {
    private final LearningOutboxMapper mapper;
    private final ObjectMapper json;
    public LearningOutboxWriter(LearningOutboxMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Transactional(transactionManager = "transactionManager", propagation = Propagation.MANDATORY)
    public void enqueue(Long userId, Long courseId, LearningOutboxPayload payload, boolean hot, boolean finish) {
        String source = UUID.randomUUID().toString();
        if (hot) insert(source, "HOT_INCREMENT", userId, courseId, payload, null, null);
        String mastery = finish && !payload.knowledgePointIds().isEmpty()
                ? insert(source, "MASTERY_UPDATE", userId, courseId, payload, null, null) : null;
        String score = insert(source, "SCORE_REFRESH", userId, courseId, payload, null, null);
        if (payload.invalidationMode() != null) {
            insert(source, "RECOMMEND_INVALIDATE", userId, courseId, payload, score, mastery);
        }
    }

    private String insert(String source, String type, Long user, Long course, LearningOutboxPayload payload,
            String dependency, String masteryDependency) {
        String id = UUID.randomUUID().toString();
        try {
            mapper.insertTask(id, source, type, user, course, json.writeValueAsString(payload),
                    dependency, masteryDependency);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("无法序列化学习事件", e);
        }
        return id;
    }
}
