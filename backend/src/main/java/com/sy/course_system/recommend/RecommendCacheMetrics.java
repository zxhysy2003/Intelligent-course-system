package com.sy.course_system.recommend;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;

@Component
public class RecommendCacheMetrics {

    private final MeterRegistry registry;
    private final Timer buildDuration;

    public RecommendCacheMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.buildDuration = Timer.builder("recommend.cache.build.duration")
                .description("推荐缓存构建耗时")
                .publishPercentiles(0.95, 0.99)
                .register(registry);
        registerPercentileGauge(0.95);
        registerPercentileGauge(0.99);
    }

    public void event(String event) {
        registry.counter("recommend.cache.events", "event", event).increment();
    }

    public void buildCompleted(long durationNanos) {
        registry.counter("recommend.cache.build.count").increment();
        buildDuration.record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private void registerPercentileGauge(double percentile) {
        registry.gauge("recommend.cache.build.duration.percentile",
                List.of(Tag.of("quantile", String.valueOf(percentile))),
                buildDuration,
                timer -> finiteOrZero(percentileValue(timer, percentile)));
    }

    /**
     * 从直方图快照读取已发布的分位数，避免使用已弃用的 Timer.percentile API。
     * 分位数值的原始单位是纳秒，这里转换成指标约定的毫秒。
     */
    private double percentileValue(Timer timer, double percentile) {
        for (ValueAtPercentile value : timer.takeSnapshot().percentileValues()) {
            if (Double.compare(value.percentile(), percentile) == 0) {
                return value.value(TimeUnit.MILLISECONDS);
            }
        }
        return 0.0d;
    }

    private double finiteOrZero(double value) {
        return Double.isFinite(value) && value >= 0.0d ? value : 0.0d;
    }
}
