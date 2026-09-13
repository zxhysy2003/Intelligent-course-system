package com.sy.course_system.outbox;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import com.sy.course_system.mapper.LearningOutboxMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;

@Repository
public class LearningOutboxStore {
    private final LearningOutboxMapper mapper;
    private final LearningOutboxProperties properties;
    public LearningOutboxStore(LearningOutboxMapper mapper, LearningOutboxProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    // 队列领取不需要可重复读；READ COMMITTED 避免状态索引的间隙锁升级死锁。
    @Transactional(transactionManager = "transactionManager", isolation = Isolation.READ_COMMITTED,
            propagation = Propagation.REQUIRES_NEW)
    public LearningOutboxTask claim() {
        // 到期回收也计入尝试上限，避免反复崩溃的任务永久循环。
        mapper.expireExhausted(properties.maxAttempts());
        LearningOutboxTask candidate = mapper.selectClaimableForUpdate(properties.maxAttempts());
        if (candidate == null) return null;
        LearningOutboxTask task = new LearningOutboxTask(candidate.id(), candidate.type(), candidate.userId(),
                candidate.courseId(), candidate.payload(), candidate.attempts() + 1, UUID.randomUUID().toString());
        mapper.markProcessing(task, properties.leaseSeconds());
        return task;
    }

    public boolean renew(LearningOutboxTask task) {
        return mapper.renew(task, properties.leaseSeconds()) == 1;
    }

    public boolean complete(LearningOutboxTask task, boolean skipped) {
        return mapper.complete(task, skipped ? "SKIPPED" : "DONE") == 1;
    }

    public void fail(LearningOutboxTask task, Exception error) {
        String message = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        mapper.fail(task, task.attempts() >= properties.maxAttempts() ? "DEAD" : "PENDING",
                retryDelay(task.attempts()), message.substring(0, Math.min(1000, message.length())));
    }

    long retryDelay(int attempts) {
        long base = Math.min(properties.retryMaxSeconds(),
                (long) properties.retryBaseSeconds() * (1L << Math.min(30, Math.max(0, attempts - 1))));
        return Math.min(properties.retryMaxSeconds(), base + ThreadLocalRandom.current().nextLong(Math.max(1, base / 5)));
    }

    public long count(String status) {
        return mapper.countByStatus(status);
    }

    public long oldestPendingSeconds() {
        return mapper.oldestPendingSeconds();
    }
}
