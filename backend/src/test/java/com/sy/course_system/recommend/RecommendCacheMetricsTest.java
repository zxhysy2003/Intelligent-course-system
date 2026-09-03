package com.sy.course_system.recommend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RecommendCacheMetricsTest {

    @Test
    void shouldExposeFiniteBuildDurationPercentilesAsGauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RecommendCacheMetrics metrics = new RecommendCacheMetrics(registry);

        for (int millis = 1; millis <= 100; millis++) {
            metrics.buildCompleted(TimeUnit.MILLISECONDS.toNanos(millis));
        }

        Gauge p95Gauge = registry.get("recommend.cache.build.duration.percentile")
                .tag("quantile", "0.95")
                .gauge();
        Gauge p99Gauge = registry.get("recommend.cache.build.duration.percentile")
                .tag("quantile", "0.99")
                .gauge();

        double p95 = p95Gauge.value();
        double p99 = p99Gauge.value();
        assertTrue(Double.isFinite(p95) && p95 > 0.0d);
        assertTrue(Double.isFinite(p99) && p99 >= p95);
    }
}
