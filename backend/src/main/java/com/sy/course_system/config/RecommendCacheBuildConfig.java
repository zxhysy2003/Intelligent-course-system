package com.sy.course_system.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 推荐结果构建专用有界线程池，拒绝时由调用方立即走轻量降级。 */
@Configuration
public class RecommendCacheBuildConfig {

    @Bean("recommendCacheBuildExecutor")
    public ThreadPoolTaskExecutor recommendCacheBuildExecutor(RecommendProperties properties) {
        RecommendProperties.Cache cache = properties.cache();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cache.buildCoreSize());
        executor.setMaxPoolSize(cache.buildMaxSize());
        executor.setQueueCapacity(cache.buildQueueCapacity());
        executor.setThreadNamePrefix("recommend-build-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }
}
