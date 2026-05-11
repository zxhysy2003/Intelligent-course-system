package com.sy.course_system.support;

import java.util.List;
import java.util.function.Consumer;

import com.sy.course_system.config.RecommendProperties;

public final class RecommendPropertiesFixture {

    private RecommendPropertiesFixture() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final RegularBuilder regular = new RegularBuilder();
        private final CacheBuilder cache = new CacheBuilder();
        private final ScoreSnapshotBuilder scoreSnapshot = new ScoreSnapshotBuilder();
        private final ScoreBuilder score = new ScoreBuilder();
        private final HotFallbackBuilder hotFallback = new HotFallbackBuilder();
        private final NewCourseBuilder newCourse = new NewCourseBuilder();
        private final GraphBuilder graph = new GraphBuilder();
        private final AsyncBuilder async = new AsyncBuilder();
        private final HotSyncBuilder hotSync = new HotSyncBuilder();

        public Builder regular(Consumer<RegularBuilder> customizer) {
            customizer.accept(regular);
            return this;
        }

        public Builder cache(Consumer<CacheBuilder> customizer) {
            customizer.accept(cache);
            return this;
        }

        public Builder scoreSnapshot(Consumer<ScoreSnapshotBuilder> customizer) {
            customizer.accept(scoreSnapshot);
            return this;
        }

        public Builder score(Consumer<ScoreBuilder> customizer) {
            customizer.accept(score);
            return this;
        }

        public Builder hotFallback(Consumer<HotFallbackBuilder> customizer) {
            customizer.accept(hotFallback);
            return this;
        }

        public Builder newCourse(Consumer<NewCourseBuilder> customizer) {
            customizer.accept(newCourse);
            return this;
        }

        public Builder graph(Consumer<GraphBuilder> customizer) {
            customizer.accept(graph);
            return this;
        }

        public Builder async(Consumer<AsyncBuilder> customizer) {
            customizer.accept(async);
            return this;
        }

        public Builder hotSync(Consumer<HotSyncBuilder> customizer) {
            customizer.accept(hotSync);
            return this;
        }

        public RecommendProperties build() {
            return new RecommendProperties(
                    regular.build(),
                    cache.build(),
                    scoreSnapshot.build(),
                    score.build(),
                    hotFallback.build(),
                    newCourse.build(),
                    graph.build(),
                    async.build(),
                    hotSync.build());
        }
    }

    public static final class RegularBuilder {
        private String serviceUrl = "http://localhost:8000";
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 30000;
        private int requestTopN = 100;
        private int coldStartLimit = 10;
        private int candidatePoolSize = 20;
        private double cfWeight = 0.7;

        public RegularBuilder serviceUrl(String serviceUrl) {
            this.serviceUrl = serviceUrl;
            return this;
        }

        public RegularBuilder connectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public RegularBuilder readTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
            return this;
        }

        public RegularBuilder requestTopN(int requestTopN) {
            this.requestTopN = requestTopN;
            return this;
        }

        public RegularBuilder coldStartLimit(int coldStartLimit) {
            this.coldStartLimit = coldStartLimit;
            return this;
        }

        public RegularBuilder candidatePoolSize(int candidatePoolSize) {
            this.candidatePoolSize = candidatePoolSize;
            return this;
        }

        public RegularBuilder cfWeight(double cfWeight) {
            this.cfWeight = cfWeight;
            return this;
        }

        private RecommendProperties.Regular build() {
            return new RecommendProperties.Regular(serviceUrl, connectTimeoutMs, readTimeoutMs, requestTopN,
                    coldStartLimit, candidatePoolSize, cfWeight);
        }
    }

    public static final class CacheBuilder {
        private long coldStartTtlMinutes = 10L;
        private long regularTtlMinutes = 30L;
        private long buildLockTtlSeconds = 20L;
        private int waitRetryTimes = 3;
        private long waitMillis = 80L;
        private long studyInvalidateThrottleSeconds = 90L;

        public CacheBuilder coldStartTtlMinutes(long coldStartTtlMinutes) {
            this.coldStartTtlMinutes = coldStartTtlMinutes;
            return this;
        }

        public CacheBuilder regularTtlMinutes(long regularTtlMinutes) {
            this.regularTtlMinutes = regularTtlMinutes;
            return this;
        }

        public CacheBuilder buildLockTtlSeconds(long buildLockTtlSeconds) {
            this.buildLockTtlSeconds = buildLockTtlSeconds;
            return this;
        }

        public CacheBuilder waitRetryTimes(int waitRetryTimes) {
            this.waitRetryTimes = waitRetryTimes;
            return this;
        }

        public CacheBuilder waitMillis(long waitMillis) {
            this.waitMillis = waitMillis;
            return this;
        }

        public CacheBuilder studyInvalidateThrottleSeconds(long studyInvalidateThrottleSeconds) {
            this.studyInvalidateThrottleSeconds = studyInvalidateThrottleSeconds;
            return this;
        }

        private RecommendProperties.Cache build() {
            return new RecommendProperties.Cache(coldStartTtlMinutes, regularTtlMinutes, buildLockTtlSeconds,
                    waitRetryTimes, waitMillis, studyInvalidateThrottleSeconds);
        }
    }

    public static final class ScoreSnapshotBuilder {
        private boolean rebuildOnStartup = true;
        private int batchSize = 500;
        private double rawScoreScale = 20.0;
        private double minScore = 0.1;

        public ScoreSnapshotBuilder rebuildOnStartup(boolean rebuildOnStartup) {
            this.rebuildOnStartup = rebuildOnStartup;
            return this;
        }

        public ScoreSnapshotBuilder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public ScoreSnapshotBuilder rawScoreScale(double rawScoreScale) {
            this.rawScoreScale = rawScoreScale;
            return this;
        }

        public ScoreSnapshotBuilder minScore(double minScore) {
            this.minScore = minScore;
            return this;
        }

        private RecommendProperties.ScoreSnapshot build() {
            return new RecommendProperties.ScoreSnapshot(rebuildOnStartup, batchSize, rawScoreScale, minScore);
        }
    }

    public static final class ScoreBuilder {
        private int base = 60;
        private int span = 35;
        private double coldStartUserScale = 10.0;
        private double hotFallbackBase = 0.70;
        private double hotFallbackStep = 0.03;
        private double hotFallbackMin = 0.55;

        public ScoreBuilder base(int base) {
            this.base = base;
            return this;
        }

        public ScoreBuilder span(int span) {
            this.span = span;
            return this;
        }

        public ScoreBuilder coldStartUserScale(double coldStartUserScale) {
            this.coldStartUserScale = coldStartUserScale;
            return this;
        }

        public ScoreBuilder hotFallbackBase(double hotFallbackBase) {
            this.hotFallbackBase = hotFallbackBase;
            return this;
        }

        public ScoreBuilder hotFallbackStep(double hotFallbackStep) {
            this.hotFallbackStep = hotFallbackStep;
            return this;
        }

        public ScoreBuilder hotFallbackMin(double hotFallbackMin) {
            this.hotFallbackMin = hotFallbackMin;
            return this;
        }

        private RecommendProperties.Score build() {
            return new RecommendProperties.Score(base, span, coldStartUserScale, hotFallbackBase, hotFallbackStep,
                    hotFallbackMin);
        }
    }

    public static final class HotFallbackBuilder {
        private int limit = 10;
        private int maxScanCount = 100;

        public HotFallbackBuilder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public HotFallbackBuilder maxScanCount(int maxScanCount) {
            this.maxScanCount = maxScanCount;
            return this;
        }

        private RecommendProperties.HotFallback build() {
            return new RecommendProperties.HotFallback(limit, maxScanCount);
        }
    }

    public static final class NewCourseBuilder {
        private boolean enabled = true;
        private int windowDays = 14;
        private int candidateLimit = 80;
        private int regularCandidateLimit = 30;
        private int fallbackLimit = 10;
        private int maxLearners = 20;
        private int injectLimit = 3;
        private double maxExposureRatio = 0.30;
        private List<Integer> injectSlots = List.of(2, 7, 12);
        private int minTagCount = 1;
        private int minKpCount = 1;
        private int minDurationSeconds = 300;
        private int minLimit = 1;
        private int maxLimit = 50;
        private int defaultLimit = 10;
        private double tagWeight = 0.45;
        private double freshnessWeight = 0.30;
        private double qualityWeight = 0.20;
        private double readinessWeight = 0.05;
        private double readinessThreshold = 0.7;
        private double learningGoalBonus = 0.05;
        private double qualityKpFullScoreCount = 4.0;
        private double qualityDurationFullScoreSeconds = 1800.0;
        private double qualityKpWeight = 0.5;

        public NewCourseBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public NewCourseBuilder windowDays(int windowDays) {
            this.windowDays = windowDays;
            return this;
        }

        public NewCourseBuilder candidateLimit(int candidateLimit) {
            this.candidateLimit = candidateLimit;
            return this;
        }

        public NewCourseBuilder regularCandidateLimit(int regularCandidateLimit) {
            this.regularCandidateLimit = regularCandidateLimit;
            return this;
        }

        public NewCourseBuilder fallbackLimit(int fallbackLimit) {
            this.fallbackLimit = fallbackLimit;
            return this;
        }

        public NewCourseBuilder maxLearners(int maxLearners) {
            this.maxLearners = maxLearners;
            return this;
        }

        public NewCourseBuilder injectLimit(int injectLimit) {
            this.injectLimit = injectLimit;
            return this;
        }

        public NewCourseBuilder maxExposureRatio(double maxExposureRatio) {
            this.maxExposureRatio = maxExposureRatio;
            return this;
        }

        public NewCourseBuilder injectSlots(List<Integer> injectSlots) {
            this.injectSlots = injectSlots;
            return this;
        }

        public NewCourseBuilder minTagCount(int minTagCount) {
            this.minTagCount = minTagCount;
            return this;
        }

        public NewCourseBuilder minKpCount(int minKpCount) {
            this.minKpCount = minKpCount;
            return this;
        }

        public NewCourseBuilder minDurationSeconds(int minDurationSeconds) {
            this.minDurationSeconds = minDurationSeconds;
            return this;
        }

        public NewCourseBuilder minLimit(int minLimit) {
            this.minLimit = minLimit;
            return this;
        }

        public NewCourseBuilder maxLimit(int maxLimit) {
            this.maxLimit = maxLimit;
            return this;
        }

        public NewCourseBuilder defaultLimit(int defaultLimit) {
            this.defaultLimit = defaultLimit;
            return this;
        }

        public NewCourseBuilder tagWeight(double tagWeight) {
            this.tagWeight = tagWeight;
            return this;
        }

        public NewCourseBuilder freshnessWeight(double freshnessWeight) {
            this.freshnessWeight = freshnessWeight;
            return this;
        }

        public NewCourseBuilder qualityWeight(double qualityWeight) {
            this.qualityWeight = qualityWeight;
            return this;
        }

        public NewCourseBuilder readinessWeight(double readinessWeight) {
            this.readinessWeight = readinessWeight;
            return this;
        }

        public NewCourseBuilder readinessThreshold(double readinessThreshold) {
            this.readinessThreshold = readinessThreshold;
            return this;
        }

        public NewCourseBuilder learningGoalBonus(double learningGoalBonus) {
            this.learningGoalBonus = learningGoalBonus;
            return this;
        }

        public NewCourseBuilder qualityKpFullScoreCount(double qualityKpFullScoreCount) {
            this.qualityKpFullScoreCount = qualityKpFullScoreCount;
            return this;
        }

        public NewCourseBuilder qualityDurationFullScoreSeconds(double qualityDurationFullScoreSeconds) {
            this.qualityDurationFullScoreSeconds = qualityDurationFullScoreSeconds;
            return this;
        }

        public NewCourseBuilder qualityKpWeight(double qualityKpWeight) {
            this.qualityKpWeight = qualityKpWeight;
            return this;
        }

        private RecommendProperties.NewCourse build() {
            return new RecommendProperties.NewCourse(enabled, windowDays, candidateLimit, regularCandidateLimit,
                    fallbackLimit, maxLearners, injectLimit, maxExposureRatio, injectSlots, minTagCount,
                    minKpCount, minDurationSeconds, minLimit, maxLimit, defaultLimit, tagWeight, freshnessWeight,
                    qualityWeight, readinessWeight, readinessThreshold, learningGoalBonus, qualityKpFullScoreCount,
                    qualityDurationFullScoreSeconds, qualityKpWeight);
        }
    }

    public static final class GraphBuilder {
        private double prerequisiteThreshold = 0.7;
        private int learningPathLimitPerCourse = 5;

        public GraphBuilder prerequisiteThreshold(double prerequisiteThreshold) {
            this.prerequisiteThreshold = prerequisiteThreshold;
            return this;
        }

        public GraphBuilder learningPathLimitPerCourse(int learningPathLimitPerCourse) {
            this.learningPathLimitPerCourse = learningPathLimitPerCourse;
            return this;
        }

        private RecommendProperties.Graph build() {
            return new RecommendProperties.Graph(prerequisiteThreshold, learningPathLimitPerCourse);
        }
    }

    public static final class AsyncBuilder {
        private boolean enabled = true;
        private int coreSize = 2;
        private int maxSize = 4;
        private int queueCapacity = 100;

        public AsyncBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public AsyncBuilder coreSize(int coreSize) {
            this.coreSize = coreSize;
            return this;
        }

        public AsyncBuilder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public AsyncBuilder queueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
            return this;
        }

        private RecommendProperties.Async build() {
            return new RecommendProperties.Async(enabled, coreSize, maxSize, queueCapacity);
        }
    }

    public static final class HotSyncBuilder {
        private boolean enabled = true;
        private long fixedDelayMs = 300000L;
        private int batchSize = 500;

        public HotSyncBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public HotSyncBuilder fixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
            return this;
        }

        public HotSyncBuilder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        private RecommendProperties.HotSync build() {
            return new RecommendProperties.HotSync(enabled, fixedDelayMs, batchSize);
        }
    }
}
