package com.sy.course_system.service.impl;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.dto.recommend.RecommendRequestDTO;
import com.sy.course_system.dto.recommend.RecommendResponseDTO;
import com.sy.course_system.dto.recommend.UserCourseScoreDTO;
import com.sy.course_system.service.LearningBehaviorService;
import com.sy.course_system.service.RecommendService;

@Service
public class RecommendServiceImpl implements RecommendService {

    private static final Logger log = LoggerFactory.getLogger(RecommendServiceImpl.class);

    private static final String SCORE_MATRIX_CACHE_KEY = "recommend:score-matrix";
    private static final String SCORE_MATRIX_LOCK_KEY = "recommend:score-matrix:lock";
    private static final long SCORE_MATRIX_LOCK_TTL_SECONDS = 20L;
    private static final int SCORE_MATRIX_WAIT_RETRY_TIMES = 3;
    private static final long SCORE_MATRIX_WAIT_MILLIS = 80L;
    private static final TypeReference<List<UserCourseScoreDTO>> SCORE_MATRIX_TYPE = new TypeReference<>() {
    };

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LearningBehaviorService learningBehaviorService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${recommend.service.url}")
    private String recommendServiceUrl;

    @Value("${recommend.score-matrix-cache.enabled:true}")
    private boolean scoreMatrixCacheEnabled;

    @Value("${recommend.score-matrix-cache.ttl-minutes:2}")
    private long scoreMatrixCacheTtlMinutes;

    @Override
    public RecommendResponseDTO recommend(Long userId) {

        List<UserCourseScoreDTO> scoreList = loadScoreMatrixSnapshot();

        // 3. 调用推荐服务
        RecommendRequestDTO request = new RecommendRequestDTO();
        request.setTargetUserId(userId);
        request.setData(scoreList);
        request.setTopN(100); // 候选池大小

        return restTemplate.postForObject(
                recommendServiceUrl + "/recommend",
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
        if (!scoreMatrixCacheEnabled || scoreMatrixCacheTtlMinutes <= 0) {
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
        Object cached = redisTemplate.opsForValue().get(SCORE_MATRIX_CACHE_KEY);
        if (cached == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(cached, SCORE_MATRIX_TYPE);
        } catch (IllegalArgumentException ex) {
            // 历史缓存结构或序列化类型异常时直接回源，避免推荐链路被单个坏缓存卡死。
            log.warn("Failed to deserialize recommend score matrix cache, rebuilding snapshot", ex);
            return null;
        }
    }

    private void writeScoreMatrixCache(List<UserCourseScoreDTO> snapshot) {
        List<UserCourseScoreDTO> safeSnapshot = snapshot == null ? List.of() : snapshot;
        redisTemplate.opsForValue().set(SCORE_MATRIX_CACHE_KEY, safeSnapshot, scoreMatrixCacheTtlMinutes,
                TimeUnit.MINUTES);
    }

    private boolean tryAcquireScoreMatrixLock() {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(SCORE_MATRIX_LOCK_KEY, "1",
                SCORE_MATRIX_LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    private List<UserCourseScoreDTO> waitForScoreMatrixCache() {
        for (int i = 0; i < SCORE_MATRIX_WAIT_RETRY_TIMES; i++) {
            sleepQuietly(SCORE_MATRIX_WAIT_MILLIS);
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
