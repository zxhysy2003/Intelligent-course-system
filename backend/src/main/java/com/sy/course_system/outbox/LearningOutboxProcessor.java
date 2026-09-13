package com.sy.course_system.outbox;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;

@Component
@EnableConfigurationProperties(LearningOutboxProperties.class)
public class LearningOutboxProcessor {
    private static final Logger log = LoggerFactory.getLogger(LearningOutboxProcessor.class);
    private final LearningOutboxStore store;
    private final LearningOutboxHandler handler;
    private final LearningOutboxProperties properties;
    private final MeterRegistry metrics;
    private final ExecutorService workers;
    private final ScheduledExecutorService renewer;
    private final Semaphore slots;
    private volatile boolean stopping;
    private volatile long pending;
    private volatile long dead;
    private volatile long oldest;

    public LearningOutboxProcessor(LearningOutboxStore store, LearningOutboxHandler handler,
            LearningOutboxProperties properties, MeterRegistry metrics) {
        this.store = store;
        this.handler = handler;
        this.properties = properties;
        this.metrics = metrics;
        workers = Executors.newFixedThreadPool(properties.parallelism());
        renewer = Executors.newScheduledThreadPool(properties.parallelism());
        slots = new Semaphore(properties.parallelism());
        metrics.gauge("learning.outbox.pending", this, p -> p.pending);
        metrics.gauge("learning.outbox.dead", this, p -> p.dead);
        metrics.gauge("learning.outbox.oldest.seconds", this, p -> p.oldest);
    }

    @Scheduled(fixedDelayString = "${learning.outbox.scan-interval-ms:1000}")
    public void scan() {
        if (stopping) return;
        try {
            pending = store.count("PENDING") + store.count("PROCESSING");
            dead = store.count("DEAD");
            oldest = store.oldestPendingSeconds();
            if (!properties.enabled()) return;
            while (!stopping && slots.tryAcquire()) {
                try {
                    LearningOutboxTask task = store.claim();
                    if (task == null) { slots.release(); break; }
                    workers.execute(() -> execute(task));
                } catch (RuntimeException e) {
                    slots.release();
                    throw e;
                }
            }
        } catch (RuntimeException e) {
            log.warn("学习 Outbox 扫描失败", e);
        }
    }

    private void execute(LearningOutboxTask task) {
        Timer.Sample timer = Timer.start(metrics);
        AtomicBoolean owned = new AtomicBoolean(true);
        var heartbeat = renewer.scheduleAtFixedRate(() -> {
            try { if (!store.renew(task)) owned.set(false); }
            catch (RuntimeException e) { log.warn("Outbox 续租失败 taskId={}", task.id(), e); }
        }, properties.renewSeconds(), properties.renewSeconds(), TimeUnit.SECONDS);
        try {
            if (!store.renew(task)) return;
            boolean skipped = handler.handle(task);
            if (owned.get() && store.complete(task, skipped)) {
                metrics.counter("learning.outbox.completed", "type", task.type()).increment();
            }
        } catch (Exception e) {
            metrics.counter("learning.outbox.failures", "type", task.type()).increment();
            log.warn("Outbox 执行失败 taskId={} attempt={}", task.id(), task.attempts(), e);
            try { if (owned.get()) store.fail(task, e); }
            catch (RuntimeException saveError) { log.warn("Outbox 失败状态保存失败 taskId={}", task.id(), saveError); }
        } finally {
            heartbeat.cancel(false);
            slots.release();
            timer.stop(metrics.timer("learning.outbox.duration", "type", task.type()));
        }
    }

    @PreDestroy
    public void close() {
        stopping = true;
        workers.shutdown();
        try { if (!workers.awaitTermination(30, TimeUnit.SECONDS)) workers.shutdownNow(); }
        catch (InterruptedException e) { workers.shutdownNow(); Thread.currentThread().interrupt(); }
        renewer.shutdownNow();
    }
}
