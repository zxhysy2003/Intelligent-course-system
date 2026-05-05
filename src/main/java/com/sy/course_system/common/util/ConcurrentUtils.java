package com.sy.course_system.common.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * CompletableFuture 结果解析工具，统一处理中断与业务异常。
 */
public final class ConcurrentUtils {

    private ConcurrentUtils() {
    }

    /**
     * 等待单个 Future 完成。
     *
     * InterruptedException 恢复中断标记后包装为 RuntimeException；
     * ExecutionException 解包后直接抛出原始 RuntimeException，其余包装。
     */
    public static <T> T await(CompletableFuture<T> future, String interruptMessage) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(interruptMessage, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        }
    }

    /**
     * 等待多个 Future 全部完成，任一线程中断或失败即抛出异常。
     */
    public static void awaitAll(String interruptMessage, CompletableFuture<?>... futures) {
        try {
            CompletableFuture.allOf(futures).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(interruptMessage, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        }
    }
}
