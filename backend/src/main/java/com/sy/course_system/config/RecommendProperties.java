package com.sy.course_system.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 推荐链路统一配置入口。
 *
 * 这里集中管理推荐相关的可调参数，避免业务类里散落 @Value 和硬编码常量。
 * 默认值与历史代码保持一致：即使 application.yaml 未显式配置，也不会改变现有行为。
 */
@Component
@ConfigurationProperties(prefix = "recommend")
public class RecommendProperties {

    private Regular regular = new Regular();
    private Cache cache = new Cache();
    private Score score = new Score();
    private HotFallback hotFallback = new HotFallback();
    private NewCourse newCourse = new NewCourse();
    private Graph graph = new Graph();
    private Async async = new Async();
    private HotSync hotSync = new HotSync();

    public Regular getRegular() {
        return regular;
    }

    public void setRegular(Regular regular) {
        this.regular = regular;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Score getScore() {
        return score;
    }

    public void setScore(Score score) {
        this.score = score;
    }

    public HotFallback getHotFallback() {
        return hotFallback;
    }

    public void setHotFallback(HotFallback hotFallback) {
        this.hotFallback = hotFallback;
    }

    public NewCourse getNewCourse() {
        return newCourse;
    }

    public void setNewCourse(NewCourse newCourse) {
        this.newCourse = newCourse;
    }

    public Graph getGraph() {
        return graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    public Async getAsync() {
        return async;
    }

    public void setAsync(Async async) {
        this.async = async;
    }

    public HotSync getHotSync() {
        return hotSync;
    }

    public void setHotSync(HotSync hotSync) {
        this.hotSync = hotSync;
    }

    public static class Regular {
        private String serviceUrl = "http://localhost:8000";
        private int connectTimeoutMs = 5000;
        private int readTimeoutMs = 30000;
        private int requestTopN = 100;
        private int coldStartLimit = 10;
        private int candidatePoolSize = 20;
        private double cfWeight = 0.7;

        public String getServiceUrl() {
            return serviceUrl;
        }

        public void setServiceUrl(String serviceUrl) {
            this.serviceUrl = serviceUrl;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public int getRequestTopN() {
            return requestTopN;
        }

        public void setRequestTopN(int requestTopN) {
            this.requestTopN = requestTopN;
        }

        public int getColdStartLimit() {
            return coldStartLimit;
        }

        public void setColdStartLimit(int coldStartLimit) {
            this.coldStartLimit = coldStartLimit;
        }

        public int getCandidatePoolSize() {
            return candidatePoolSize;
        }

        public void setCandidatePoolSize(int candidatePoolSize) {
            this.candidatePoolSize = candidatePoolSize;
        }

        public double getCfWeight() {
            return cfWeight;
        }

        public void setCfWeight(double cfWeight) {
            this.cfWeight = cfWeight;
        }
    }

    public static class Cache {
        private long coldStartTtlMinutes = 10L;
        private long regularTtlMinutes = 30L;
        private long buildLockTtlSeconds = 20L;
        private int waitRetryTimes = 3;
        private long waitMillis = 80L;
        private boolean scoreMatrixEnabled = true;
        private long scoreMatrixTtlMinutes = 2L;
        private long scoreMatrixLockTtlSeconds = 20L;
        private int scoreMatrixWaitRetryTimes = 3;
        private long scoreMatrixWaitMillis = 80L;

        public long getColdStartTtlMinutes() {
            return coldStartTtlMinutes;
        }

        public void setColdStartTtlMinutes(long coldStartTtlMinutes) {
            this.coldStartTtlMinutes = coldStartTtlMinutes;
        }

        public long getRegularTtlMinutes() {
            return regularTtlMinutes;
        }

        public void setRegularTtlMinutes(long regularTtlMinutes) {
            this.regularTtlMinutes = regularTtlMinutes;
        }

        public long getBuildLockTtlSeconds() {
            return buildLockTtlSeconds;
        }

        public void setBuildLockTtlSeconds(long buildLockTtlSeconds) {
            this.buildLockTtlSeconds = buildLockTtlSeconds;
        }

        public int getWaitRetryTimes() {
            return waitRetryTimes;
        }

        public void setWaitRetryTimes(int waitRetryTimes) {
            this.waitRetryTimes = waitRetryTimes;
        }

        public long getWaitMillis() {
            return waitMillis;
        }

        public void setWaitMillis(long waitMillis) {
            this.waitMillis = waitMillis;
        }

        public boolean isScoreMatrixEnabled() {
            return scoreMatrixEnabled;
        }

        public void setScoreMatrixEnabled(boolean scoreMatrixEnabled) {
            this.scoreMatrixEnabled = scoreMatrixEnabled;
        }

        public long getScoreMatrixTtlMinutes() {
            return scoreMatrixTtlMinutes;
        }

        public void setScoreMatrixTtlMinutes(long scoreMatrixTtlMinutes) {
            this.scoreMatrixTtlMinutes = scoreMatrixTtlMinutes;
        }

        public long getScoreMatrixLockTtlSeconds() {
            return scoreMatrixLockTtlSeconds;
        }

        public void setScoreMatrixLockTtlSeconds(long scoreMatrixLockTtlSeconds) {
            this.scoreMatrixLockTtlSeconds = scoreMatrixLockTtlSeconds;
        }

        public int getScoreMatrixWaitRetryTimes() {
            return scoreMatrixWaitRetryTimes;
        }

        public void setScoreMatrixWaitRetryTimes(int scoreMatrixWaitRetryTimes) {
            this.scoreMatrixWaitRetryTimes = scoreMatrixWaitRetryTimes;
        }

        public long getScoreMatrixWaitMillis() {
            return scoreMatrixWaitMillis;
        }

        public void setScoreMatrixWaitMillis(long scoreMatrixWaitMillis) {
            this.scoreMatrixWaitMillis = scoreMatrixWaitMillis;
        }
    }

    public static class Score {
        private int base = 60;
        private int span = 35;
        private double coldStartUserScale = 10.0;
        private double hotFallbackBase = 0.70;
        private double hotFallbackStep = 0.03;
        private double hotFallbackMin = 0.55;

        public int getBase() {
            return base;
        }

        public void setBase(int base) {
            this.base = base;
        }

        public int getSpan() {
            return span;
        }

        public void setSpan(int span) {
            this.span = span;
        }

        public double getColdStartUserScale() {
            return coldStartUserScale;
        }

        public void setColdStartUserScale(double coldStartUserScale) {
            this.coldStartUserScale = coldStartUserScale;
        }

        public double getHotFallbackBase() {
            return hotFallbackBase;
        }

        public void setHotFallbackBase(double hotFallbackBase) {
            this.hotFallbackBase = hotFallbackBase;
        }

        public double getHotFallbackStep() {
            return hotFallbackStep;
        }

        public void setHotFallbackStep(double hotFallbackStep) {
            this.hotFallbackStep = hotFallbackStep;
        }

        public double getHotFallbackMin() {
            return hotFallbackMin;
        }

        public void setHotFallbackMin(double hotFallbackMin) {
            this.hotFallbackMin = hotFallbackMin;
        }
    }

    public static class HotFallback {
        private int limit = 10;
        private int maxScanCount = 100;

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public int getMaxScanCount() {
            return maxScanCount;
        }

        public void setMaxScanCount(int maxScanCount) {
            this.maxScanCount = maxScanCount;
        }
    }

    public static class NewCourse {
        private boolean enabled = true;
        private int windowDays = 14;
        private int candidateLimit = 80;
        private int regularCandidateLimit = 30;
        private int fallbackLimit = 10;
        private int maxLearners = 20;
        private int injectLimit = 3;
        private double maxExposureRatio = 0.30;
        private List<Integer> injectSlots = new ArrayList<>(List.of(2, 7, 12));
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getWindowDays() {
            return windowDays;
        }

        public void setWindowDays(int windowDays) {
            this.windowDays = windowDays;
        }

        public int getCandidateLimit() {
            return candidateLimit;
        }

        public void setCandidateLimit(int candidateLimit) {
            this.candidateLimit = candidateLimit;
        }

        public int getRegularCandidateLimit() {
            return regularCandidateLimit;
        }

        public void setRegularCandidateLimit(int regularCandidateLimit) {
            this.regularCandidateLimit = regularCandidateLimit;
        }

        public int getFallbackLimit() {
            return fallbackLimit;
        }

        public void setFallbackLimit(int fallbackLimit) {
            this.fallbackLimit = fallbackLimit;
        }

        public int getMaxLearners() {
            return maxLearners;
        }

        public void setMaxLearners(int maxLearners) {
            this.maxLearners = maxLearners;
        }

        public int getInjectLimit() {
            return injectLimit;
        }

        public void setInjectLimit(int injectLimit) {
            this.injectLimit = injectLimit;
        }

        public double getMaxExposureRatio() {
            return maxExposureRatio;
        }

        public void setMaxExposureRatio(double maxExposureRatio) {
            this.maxExposureRatio = maxExposureRatio;
        }

        public List<Integer> getInjectSlots() {
            return injectSlots;
        }

        public void setInjectSlots(List<Integer> injectSlots) {
            this.injectSlots = injectSlots;
        }

        public int getMinTagCount() {
            return minTagCount;
        }

        public void setMinTagCount(int minTagCount) {
            this.minTagCount = minTagCount;
        }

        public int getMinKpCount() {
            return minKpCount;
        }

        public void setMinKpCount(int minKpCount) {
            this.minKpCount = minKpCount;
        }

        public int getMinDurationSeconds() {
            return minDurationSeconds;
        }

        public void setMinDurationSeconds(int minDurationSeconds) {
            this.minDurationSeconds = minDurationSeconds;
        }

        public int getMinLimit() {
            return minLimit;
        }

        public void setMinLimit(int minLimit) {
            this.minLimit = minLimit;
        }

        public int getMaxLimit() {
            return maxLimit;
        }

        public void setMaxLimit(int maxLimit) {
            this.maxLimit = maxLimit;
        }

        public int getDefaultLimit() {
            return defaultLimit;
        }

        public void setDefaultLimit(int defaultLimit) {
            this.defaultLimit = defaultLimit;
        }

        public double getTagWeight() {
            return tagWeight;
        }

        public void setTagWeight(double tagWeight) {
            this.tagWeight = tagWeight;
        }

        public double getFreshnessWeight() {
            return freshnessWeight;
        }

        public void setFreshnessWeight(double freshnessWeight) {
            this.freshnessWeight = freshnessWeight;
        }

        public double getQualityWeight() {
            return qualityWeight;
        }

        public void setQualityWeight(double qualityWeight) {
            this.qualityWeight = qualityWeight;
        }

        public double getReadinessWeight() {
            return readinessWeight;
        }

        public void setReadinessWeight(double readinessWeight) {
            this.readinessWeight = readinessWeight;
        }

        public double getReadinessThreshold() {
            return readinessThreshold;
        }

        public void setReadinessThreshold(double readinessThreshold) {
            this.readinessThreshold = readinessThreshold;
        }

        public double getLearningGoalBonus() {
            return learningGoalBonus;
        }

        public void setLearningGoalBonus(double learningGoalBonus) {
            this.learningGoalBonus = learningGoalBonus;
        }

        public double getQualityKpFullScoreCount() {
            return qualityKpFullScoreCount;
        }

        public void setQualityKpFullScoreCount(double qualityKpFullScoreCount) {
            this.qualityKpFullScoreCount = qualityKpFullScoreCount;
        }

        public double getQualityDurationFullScoreSeconds() {
            return qualityDurationFullScoreSeconds;
        }

        public void setQualityDurationFullScoreSeconds(double qualityDurationFullScoreSeconds) {
            this.qualityDurationFullScoreSeconds = qualityDurationFullScoreSeconds;
        }

        public double getQualityKpWeight() {
            return qualityKpWeight;
        }

        public void setQualityKpWeight(double qualityKpWeight) {
            this.qualityKpWeight = qualityKpWeight;
        }
    }

    public static class Graph {
        private double prerequisiteThreshold = 0.7;
        private int learningPathLimitPerCourse = 5;

        public double getPrerequisiteThreshold() {
            return prerequisiteThreshold;
        }

        public void setPrerequisiteThreshold(double prerequisiteThreshold) {
            this.prerequisiteThreshold = prerequisiteThreshold;
        }

        public int getLearningPathLimitPerCourse() {
            return learningPathLimitPerCourse;
        }

        public void setLearningPathLimitPerCourse(int learningPathLimitPerCourse) {
            this.learningPathLimitPerCourse = learningPathLimitPerCourse;
        }
    }

    public static class Async {
        private boolean enabled = true;
        private int coreSize = 2;
        private int maxSize = 4;
        private int queueCapacity = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCoreSize() {
            return coreSize;
        }

        public void setCoreSize(int coreSize) {
            this.coreSize = coreSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }

    public static class HotSync {
        private boolean enabled = true;
        private long fixedDelayMs = 300000L;
        private int batchSize = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
