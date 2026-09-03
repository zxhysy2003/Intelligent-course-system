package com.sy.course_system.recommend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RecommendBuildCoordinatorTest {

    @Test
    void sameKeyShouldShareOneInFlightBuild() {
        List<Runnable> queued = new ArrayList<>();
        RecommendBuildCoordinator coordinator = new RecommendBuildCoordinator(queued::add);
        AtomicInteger builds = new AtomicInteger();

        var first = coordinator.submit("key", () -> response(builds.incrementAndGet()));
        var second = coordinator.submit("key", () -> response(builds.incrementAndGet()));

        assertSame(first, second);
        assertEquals(1, queued.size());
        assertEquals(1, coordinator.inFlightCount());

        queued.get(0).run();

        assertEquals(1, first.join().getUserId());
        assertEquals(1, builds.get());
        assertEquals(0, coordinator.inFlightCount());
    }

    @Test
    void rejectedExecutorShouldRejectWithoutLeavingInFlightEntry() {
        Executor rejecting = task -> {
            throw new RejectedExecutionException("full");
        };
        RecommendBuildCoordinator coordinator = new RecommendBuildCoordinator(rejecting);

        assertThrows(RejectedExecutionException.class,
                () -> coordinator.submit("key", () -> response(1)));
        assertThrows(RejectedExecutionException.class,
                () -> coordinator.submit("key", () -> response(2)));
        assertEquals(0, coordinator.inFlightCount());
    }

    @Test
    void metricsShouldIncludeQueuedAndRunningBuilds() {
        List<Runnable> queued = new ArrayList<>();
        RecommendBuildCoordinator coordinator = new RecommendBuildCoordinator(queued::add);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new RecommendBuildCoordinatorMetrics(registry, coordinator);

        assertEquals(0.0d, registry.get("recommend.cache.build.inflight").gauge().value());

        coordinator.submit("key", () -> response(1));
        assertEquals(1.0d, registry.get("recommend.cache.build.inflight").gauge().value());

        queued.get(0).run();
        assertEquals(0.0d, registry.get("recommend.cache.build.inflight").gauge().value());
    }

    private HybridRecommendResponseDTO response(long userId) {
        return new HybridRecommendResponseDTO(userId, List.of());
    }
}
