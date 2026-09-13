package com.sy.course_system.outbox;

public record LearningOutboxTask(String id, String type, Long userId, Long courseId,
        String payload, int attempts, String leaseToken) { }
