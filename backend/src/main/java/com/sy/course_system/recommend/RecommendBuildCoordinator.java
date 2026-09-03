package com.sy.course_system.recommend;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;

/**
 * 单 JVM 推荐构建 single-flight：同一 cache key 共享一个 future，不同 key 受有界线程池保护。
 */
@Component
public class RecommendBuildCoordinator {

    private final Executor executor;
    private final ConcurrentHashMap<String, CompletableFuture<HybridRecommendResponseDTO>> inFlight =
            new ConcurrentHashMap<>();

    public RecommendBuildCoordinator(@Qualifier("recommendCacheBuildExecutor") Executor executor) {
        this.executor = executor;
    }

    public CompletableFuture<HybridRecommendResponseDTO> submit(String cacheKey,
            Supplier<HybridRecommendResponseDTO> task) {
        CompletableFuture<HybridRecommendResponseDTO> created = new CompletableFuture<>();
        CompletableFuture<HybridRecommendResponseDTO> existing = inFlight.putIfAbsent(cacheKey, created);
        if (existing != null) {
            return existing;
        }

        try {
            executor.execute(() -> {
                try {
                    created.complete(task.get());
                } catch (Throwable throwable) {
                    created.completeExceptionally(throwable);
                } finally {
                    inFlight.remove(cacheKey, created);
                }
            });
            return created;
        } catch (RejectedExecutionException ex) {
            inFlight.remove(cacheKey, created);
            created.completeExceptionally(ex);
            throw ex;
        }
    }

    /**
     * 包含正在执行和仍在队列中的构建。实验收尾必须等该值归零，不能只观察上游请求瞬时并发。
     */
    public int inFlightCount() {
        return inFlight.size();
    }
}
