package com.sy.course_system.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 推荐链路统一配置入口。
 *
 * 这里集中管理推荐相关的可调参数，避免业务类里散落 @Value 和硬编码常量。
 * 默认值与历史代码保持一致：即使 application.yaml 未显式配置，也不会改变现有行为。
 */
@ConfigurationProperties(prefix = "recommend")
public record RecommendProperties(
        @DefaultValue Regular regular,
        @DefaultValue Cache cache,
        @DefaultValue Score score,
        @DefaultValue HotFallback hotFallback,
        @DefaultValue NewCourse newCourse,
        @DefaultValue Graph graph,
        @DefaultValue Async async,
        @DefaultValue HotSync hotSync) {

    public record Regular(
            @DefaultValue("http://localhost:8000") String serviceUrl,
            @DefaultValue("5000") int connectTimeoutMs,
            @DefaultValue("30000") int readTimeoutMs,
            @DefaultValue("100") int requestTopN,
            @DefaultValue("10") int coldStartLimit,
            @DefaultValue("20") int candidatePoolSize,
            @DefaultValue("0.7") double cfWeight) {
    }

    public record Cache(
            @DefaultValue("10") long coldStartTtlMinutes,
            @DefaultValue("30") long regularTtlMinutes,
            @DefaultValue("20") long buildLockTtlSeconds,
            @DefaultValue("3") int waitRetryTimes,
            @DefaultValue("80") long waitMillis,
            @DefaultValue("true") boolean scoreMatrixEnabled,
            @DefaultValue("2") long scoreMatrixTtlMinutes,
            @DefaultValue("20") long scoreMatrixLockTtlSeconds,
            @DefaultValue("3") int scoreMatrixWaitRetryTimes,
            @DefaultValue("80") long scoreMatrixWaitMillis) {
    }

    public record Score(
            @DefaultValue("60") int base,
            @DefaultValue("35") int span,
            @DefaultValue("10.0") double coldStartUserScale,
            @DefaultValue("0.70") double hotFallbackBase,
            @DefaultValue("0.03") double hotFallbackStep,
            @DefaultValue("0.55") double hotFallbackMin) {
    }

    public record HotFallback(
            @DefaultValue("10") int limit,
            @DefaultValue("100") int maxScanCount) {
    }

    public record NewCourse(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("14") int windowDays,
            @DefaultValue("80") int candidateLimit,
            @DefaultValue("30") int regularCandidateLimit,
            @DefaultValue("10") int fallbackLimit,
            @DefaultValue("20") int maxLearners,
            @DefaultValue("3") int injectLimit,
            @DefaultValue("0.30") double maxExposureRatio,
            @DefaultValue({ "2", "7", "12" }) List<Integer> injectSlots,
            @DefaultValue("1") int minTagCount,
            @DefaultValue("1") int minKpCount,
            @DefaultValue("300") int minDurationSeconds,
            @DefaultValue("1") int minLimit,
            @DefaultValue("50") int maxLimit,
            @DefaultValue("10") int defaultLimit,
            @DefaultValue("0.45") double tagWeight,
            @DefaultValue("0.30") double freshnessWeight,
            @DefaultValue("0.20") double qualityWeight,
            @DefaultValue("0.05") double readinessWeight,
            @DefaultValue("0.7") double readinessThreshold,
            @DefaultValue("0.05") double learningGoalBonus,
            @DefaultValue("4.0") double qualityKpFullScoreCount,
            @DefaultValue("1800.0") double qualityDurationFullScoreSeconds,
            @DefaultValue("0.5") double qualityKpWeight) {

        public NewCourse {
            injectSlots = injectSlots == null ? List.of(2, 7, 12) : List.copyOf(injectSlots);
        }
    }

    public record Graph(
            @DefaultValue("0.7") double prerequisiteThreshold,
            @DefaultValue("5") int learningPathLimitPerCourse) {
    }

    public record Async(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("2") int coreSize,
            @DefaultValue("4") int maxSize,
            @DefaultValue("100") int queueCapacity) {
    }

    public record HotSync(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("300000") long fixedDelayMs,
            @DefaultValue("500") int batchSize) {
    }
}
