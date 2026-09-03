package com.sy.course_system.recommend;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 暴露推荐构建协调器中“排队 + 执行中”的任务数，供运维观测和并发实验确定后台任务已排空。
 */
@Component
public class RecommendBuildCoordinatorMetrics {

    public RecommendBuildCoordinatorMetrics(MeterRegistry registry,
            RecommendBuildCoordinator coordinator) {
        Gauge.builder("recommend.cache.build.inflight", coordinator,
                        RecommendBuildCoordinator::inFlightCount)
                .description("排队或执行中的推荐缓存构建任务数")
                .register(registry);
    }
}
