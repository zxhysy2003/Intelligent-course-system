package com.sy.course_system.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 推荐链路专用线程池配置。
 *
 * 该线程池只服务于推荐主链路中互不依赖的 IO 调用并行化，不与 @Async 或
 * 其他业务共用，避免推荐突发流量影响全局异步能力。
 *
 * 拒绝策略采用 CallerRunsPolicy：当线程池满时回退到当前请求线程同步执行，
 * 优先保证接口可用，而不是丢弃任务或抛异常。
 */
@Configuration
public class RecommendAsyncConfig {

    @Value("${recommend.async.core-size:2}")
    private int coreSize;

    @Value("${recommend.async.max-size:4}")
    private int maxSize;

    @Value("${recommend.async.queue-capacity:100}")
    private int queueCapacity;

    @Bean("recommendTaskExecutor")
    public ThreadPoolTaskExecutor recommendTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("recommend-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
