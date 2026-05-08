package com.sy.course_system.client;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.recommend.RecommendRequestDTO;
import com.sy.course_system.dto.recommend.RecommendResponseDTO;
import com.sy.course_system.dto.recommend.UserCourseScoreDTO;
import com.sy.course_system.service.LearningBehaviorService;

/**
 * 外部协同过滤推荐服务客户端。
 *
 * 负责准备 CF 服务请求所需的评分矩阵快照，并封装 HTTP 调用细节；
 * 推荐融合、过滤和图谱解释仍由 HybridRecommendServiceImpl 负责。
 */
@Component
public class CfRecommendClient {

    private static final Logger log = LoggerFactory.getLogger(CfRecommendClient.class);

    private static final String SCORE_MATRIX_CACHE_KEY = "recommend:score-matrix";
    private static final String SCORE_MATRIX_LOCK_KEY = "recommend:score-matrix:lock";
    private static final TypeReference<List<UserCourseScoreDTO>> SCORE_MATRIX_TYPE = new TypeReference<>() {
    };

    private final RestTemplate restTemplate;
    private final LearningBehaviorService learningBehaviorService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RecommendProperties recommendProperties;

    public CfRecommendClient(RestTemplate restTemplate,
            LearningBehaviorService learningBehaviorService,
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            RecommendProperties recommendProperties) {
        this.restTemplate = restTemplate;
        this.learningBehaviorService = learningBehaviorService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.recommendProperties = recommendProperties;
    }

    public RecommendResponseDTO recommend(Long userId) {

        List<UserCourseScoreDTO> scoreList = loadScoreMatrixSnapshot();

        // 调用外部 CF 推荐服务。
        RecommendRequestDTO request = new RecommendRequestDTO();
        request.setTargetUserId(userId);
        request.setData(scoreList);
        request.setTopN(recommendProperties.regular().requestTopN());

        return restTemplate.postForObject(
                recommendProperties.regular().serviceUrl() + "/recommend",
                request,
                RecommendResponseDTO.class);
    }

    /**
     * 读取全量 CF 评分矩阵快照。
     *
     * 该矩阵是外部协同过滤服务的完整输入，原先每次推荐请求都会实时聚合一次
     * learning_behavior；这里用短 TTL 缓存复用快照，避免多用户推荐缓存 miss 时重复扫表。
     */
    private List<UserCourseScoreDTO> loadScoreMatrixSnapshot() {
        RecommendProperties.Cache cache = recommendProperties.cache();
        if (!cache.scoreMatrixEnabled() || cache.scoreMatrixTtlMinutes() <= 0) {
            return buildScoreMatrixSnapshot();
        }

        List<UserCourseScoreDTO> cached = readScoreMatrixCache();
        if (cached != null) {
            return cached;
        }

        boolean locked = tryAcquireScoreMatrixLock();
        if (!locked) {
            List<UserCourseScoreDTO> waited = waitForScoreMatrixCache();
            if (waited != null) {
                return waited;
            }

            List<UserCourseScoreDTO> fallback = buildScoreMatrixSnapshot();
            writeScoreMatrixCache(fallback);
            return fallback;
        }

        try {
            List<UserCourseScoreDTO> doubleChecked = readScoreMatrixCache();
            if (doubleChecked != null) {
                return doubleChecked;
            }

            List<UserCourseScoreDTO> snapshot = buildScoreMatrixSnapshot();
            writeScoreMatrixCache(snapshot);
            return snapshot;
        } finally {
            redisTemplate.delete(SCORE_MATRIX_LOCK_KEY);
        }
    }

    private List<UserCourseScoreDTO> readScoreMatrixCache() {
        Object cached;
        try {
            cached = redisTemplate.opsForValue().get(SCORE_MATRIX_CACHE_KEY);
        } catch (SerializationException ex) {
            return evictBadScoreMatrixCache("Failed to deserialize recommend score matrix cache, rebuilding snapshot", ex);
        }
        if (cached == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(cached, SCORE_MATRIX_TYPE);
        } catch (IllegalArgumentException ex) {
            return evictBadScoreMatrixCache("Failed to convert recommend score matrix cache, rebuilding snapshot", ex);
        }
    }

    private List<UserCourseScoreDTO> evictBadScoreMatrixCache(String message, RuntimeException ex) {
        // 历史缓存结构或序列化类型异常时直接回源，避免推荐链路被单个坏缓存卡死。
        log.warn(message, ex);
        redisTemplate.delete(SCORE_MATRIX_CACHE_KEY);
        return null;
    }

    private void writeScoreMatrixCache(List<UserCourseScoreDTO> snapshot) {
        List<UserCourseScoreDTO> safeSnapshot = snapshot == null ? List.of() : snapshot;
        redisTemplate.opsForValue().set(SCORE_MATRIX_CACHE_KEY, safeSnapshot,
                recommendProperties.cache().scoreMatrixTtlMinutes(),
                TimeUnit.MINUTES);
    }

    private boolean tryAcquireScoreMatrixLock() {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(SCORE_MATRIX_LOCK_KEY, "1",
                recommendProperties.cache().scoreMatrixLockTtlSeconds(), TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    private List<UserCourseScoreDTO> waitForScoreMatrixCache() {
        int retryTimes = Math.max(recommendProperties.cache().scoreMatrixWaitRetryTimes(), 0);
        long waitMillis = Math.max(recommendProperties.cache().scoreMatrixWaitMillis(), 0L);
        for (int i = 0; i < retryTimes; i++) {
            sleepQuietly(waitMillis);
            List<UserCourseScoreDTO> cached = readScoreMatrixCache();
            if (cached != null) {
                return cached;
            }
        }
        return null;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private List<UserCourseScoreDTO> buildScoreMatrixSnapshot() {
        List<UserCourseScoreDTO> scores = learningBehaviorService.listAggregatedScores();
        return scores == null ? List.of() : scores;
    }

}
