package com.sy.course_system.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 推荐链路统一配置入口。
 *
 * 这里集中管理推荐相关的可调参数，避免业务类里散落 @Value 和硬编码常量。
 * record 上的默认值与 application.yaml 保持一致，便于配置绑定测试和最小化部署配置。
 */
@ConfigurationProperties(prefix = "recommend")
public record RecommendProperties(
        @DefaultValue Regular regular,
        @DefaultValue Cache cache,
        @DefaultValue ScoreSnapshot scoreSnapshot,
        @DefaultValue Score score,
        @DefaultValue HotFallback hotFallback,
        @DefaultValue NewCourse newCourse,
        @DefaultValue Graph graph,
        @DefaultValue Async async,
        @DefaultValue HotSync hotSync) {

    public RecommendProperties {
        long lockBudgetMs = cache.buildLockTtlSeconds() * 1000L;
        long minimumBudgetMs = (long) regular.connectTimeoutMs()
                + regular.readTimeoutMs()
                + cache.initialBuildWaitMillis();
        if (lockBudgetMs <= minimumBudgetMs) {
            throw new IllegalArgumentException(
                    "recommend.cache.build-lock-ttl-seconds must exceed the CF timeout and request wait budget");
        }
    }

    public record Regular(
            @DefaultValue("http://localhost:8000") String serviceUrl,
            @DefaultValue("500") int connectTimeoutMs,
            @DefaultValue("2000") int readTimeoutMs,
            @DefaultValue("100") int requestTopN,
            @DefaultValue("10") int coldStartLimit,
            @DefaultValue("20") int candidatePoolSize,
            @DefaultValue("0.7") double cfWeight) {
    }

    public record Cache(
            @DefaultValue("10") long coldStartTtlMinutes,
            @DefaultValue("30") long regularTtlMinutes,
            @DefaultValue("3") long coldStartTtlJitterMinutes,
            @DefaultValue("10") long regularTtlJitterMinutes,
            @DefaultValue("60") long staleRetentionMinutes,
            @DefaultValue("20") long buildLockTtlSeconds,
            @DefaultValue("3") int waitRetryTimes,
            @DefaultValue("80") long waitMillis,
            @DefaultValue("2500") long initialBuildWaitMillis,
            @DefaultValue("2") int buildCoreSize,
            @DefaultValue("4") int buildMaxSize,
            @DefaultValue("16") int buildQueueCapacity,
            @DefaultValue("300000") long fallbackRefreshMillis,
            @DefaultValue("0") long fallbackInitialDelayMillis,
            @DefaultValue("90") long studyInvalidateThrottleSeconds) {

        public Cache {
            if (coldStartTtlMinutes <= 0 || regularTtlMinutes <= 0 || staleRetentionMinutes <= 0) {
                throw new IllegalArgumentException("recommend cache TTL values must be positive");
            }
            if (coldStartTtlJitterMinutes < 0 || regularTtlJitterMinutes < 0) {
                throw new IllegalArgumentException("recommend cache TTL jitter must not be negative");
            }
            if (buildCoreSize <= 0 || buildMaxSize < buildCoreSize || buildQueueCapacity < 0) {
                throw new IllegalArgumentException("invalid recommend cache build executor configuration");
            }
            if (initialBuildWaitMillis < 0 || waitRetryTimes < 0 || waitMillis < 0) {
                throw new IllegalArgumentException("recommend cache wait settings must not be negative");
            }
            if (fallbackRefreshMillis <= 0 || fallbackInitialDelayMillis < 0) {
                throw new IllegalArgumentException("invalid recommend fallback snapshot schedule configuration");
            }
        }
    }

    public record ScoreSnapshot(
            @DefaultValue("true") boolean rebuildOnStartup,
            @DefaultValue("500") int batchSize,
            @DefaultValue("20.0") double rawScoreScale,
            @DefaultValue("0.1") double minScore) {
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
