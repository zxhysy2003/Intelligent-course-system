package com.sy.course_system.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 学习助手上下文加载线程池。
 *
 * 该线程池只承载 Agent 对外部/较慢上下文的短预算加载，不复用推荐链路线程池，
 * 避免 Agent 外层等待与推荐内部并行任务互相占用线程。
 */
@Configuration
public class AgentAsyncConfig {

    @Bean("agentContextExecutor")
    public ThreadPoolTaskExecutor agentContextExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("agent-context-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
