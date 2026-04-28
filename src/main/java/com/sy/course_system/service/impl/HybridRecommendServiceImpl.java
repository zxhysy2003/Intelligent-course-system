package com.sy.course_system.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.dto.graph.CourseKnowledgePointDTO;
import com.sy.course_system.dto.recommend.CourseReadinessDTO;
import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;
import com.sy.course_system.dto.recommend.RecommendItemDTO;
import com.sy.course_system.dto.recommend.RecommendResponseDTO;
import com.sy.course_system.entity.Course;
import com.sy.course_system.repository.CourseGraphRepository;
import com.sy.course_system.service.ColdStartRecommendService;
import com.sy.course_system.service.ColdStartSupportService;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.HybridRecommendService;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.service.NewCourseRecommendService;
import com.sy.course_system.service.RecommendService;
import com.sy.course_system.vo.ColdStartRecommendItemVO;
import com.sy.course_system.vo.KnowledgeMasteryVO;
import com.sy.course_system.vo.KnowledgeVO;

/**
 * 混合推荐服务实现：将协同过滤（CF）结果与知识图谱（KG）信号融合，输出可解释推荐。
 *
 * 核心职责分为两层：
 * 1) 召回与排序：先使用 CF 给出候选课程及相关性分，再结合先修可学习性（readiness）做融合排序。
 * 2) 解释与补全：为每条推荐补充知识点、缺失先修、学习路径，便于前端展示“为什么推荐、怎么补齐”。
 *
 * 推荐路径分支：
 * - 冷启动用户：走冷启动推荐，再复用图谱补全逻辑统一填充解释字段。
 * - 非冷启动用户：先查缓存，未命中时执行 CF + 图谱融合流程并回写缓存。
 */
@Service
public class HybridRecommendServiceImpl implements HybridRecommendService {

    private static final Logger log = LoggerFactory.getLogger(HybridRecommendServiceImpl.class);

    @Autowired
    private RecommendService recommendService;
    @Autowired
    private CourseGraphRepository courseGraphRepository;
    @Autowired
    private Neo4jClient neo4jClient;
    @Autowired
    private CourseService courseService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ColdStartSupportService coldStartSupportService;
    @Autowired
    private ColdStartRecommendService coldStartRecommendService;
    @Autowired
    private NewCourseRecommendService newCourseRecommendService;
    @Autowired
    private LearningAnalysisService learningAnalysisService;

    // 常规推荐缓存 Key 前缀，完整 key 形如 recommend:user:{userId}
    private static final String RECOMMEND_COURSE_KEY = "recommend:user:";
    // 冷启动推荐缓存 Key 前缀，与常规推荐缓存隔离
    private static final String RECOMMEND_COLD_START_KEY = "recommend:cold:user:";
    // 常规推荐构建锁 Key 前缀，用于缓存未命中时的并发回源保护
    private static final String RECOMMEND_COURSE_LOCK_KEY = "recommend:lock:user:";
    // 冷启动推荐构建锁 Key 前缀，避免与常规推荐抢同一把锁
    private static final String RECOMMEND_COLD_START_LOCK_KEY = "recommend:cold:lock:user:";
    // 冷启动推荐缓存过期时间（分钟）
    private static final long COLD_START_CACHE_TTL_MINUTES = 10L;
    // 常规推荐缓存过期时间（分钟）
    private static final long RECOMMEND_CACHE_TTL_MINUTES = 30L;
    // 缓存构建锁过期时间（秒），防止异常导致锁长期不释放
    private static final long CACHE_BUILD_LOCK_TTL_SECONDS = 20L;
    // 未获取到构建锁时的缓存重试次数
    private static final int CACHE_WAIT_RETRY_TIMES = 3;
    // 每次重试前的等待时间（毫秒）
    private static final long CACHE_WAIT_MILLIS = 80L;

    // 冷启动返回条数：控制初始曝光规模，避免冷启动阶段结果过多
    private static final int COLD_START_LIMIT = 10;
    // 热门课程兜底返回上限
    private static final int HOT_FALLBACK_LIMIT = 10;
    // 热门兜底每轮扫描的热榜区间大小
    private static final int HOT_FALLBACK_SCAN_BATCH_SIZE = HOT_FALLBACK_LIMIT;
    // 热门兜底最多扫描的热榜课程数，避免在极端脏数据下无限查询
    private static final int HOT_FALLBACK_MAX_SCAN_COUNT = 100;
    // 常规推荐为空时，新课兜底返回上限
    private static final int NEW_COURSE_FALLBACK_LIMIT = 10;
    // 常规推荐阶段查询的新课候选上限
    private static final int NEW_COURSE_CANDIDATE_LIMIT = 30;
    // 新课插槽位置（0-based）：选择第 3、8、13 位，分别覆盖结果列表的前段、中段和后段，
    // 在保证新课有稳定曝光的同时，避免将新课过度集中在列表顶部而影响常规推荐体验。
    private static final int[] NEW_COURSE_INJECT_SLOTS = new int[] { 2, 7, 12 };

    // CF 候选池上限：先截断再做图谱计算，减少 Neo4j 批量查询压力
    private static final int CANDIDATE_POOL_SIZE = 20;

    // 先修掌握阈值：小于该值的知识点视为“尚未掌握”，会进入缺失先修或补齐路径
    private static final double PREREQUISITE_THRESHOLD = 0.7;
    // 每门推荐课程最多返回的补齐路径数，批量查询时仍按单课程维度截断。
    private static final int LEARNING_PATH_LIMIT_PER_COURSE = 5;

    // 融合分中 CF 的权重（readiness 权重为 1 - CF_WEIGHT）
    private static final double CF_WEIGHT = 0.7;

    // 可学习性过滤阈值（当前过滤逻辑暂未启用，保留用于策略开关）
    @SuppressWarnings("unused")
    private static final double READINESS_THRESHOLD = 0.4;

    // 展示分基线：统一把不同来源的推荐结果映射到 60~95 区间，便于前端稳定展示。
    // 这里故意不映射到 0~100：
    // - 过低分数容易被用户理解为“系统不推荐却硬塞给我”；
    // - 过高分数又容易被误读成算法置信度接近百分百。
    // 因此选择中高位区间，只表达“相对推荐度”，不表达概率含义。
    private static final int RECOMMEND_SCORE_BASE = 60;
    private static final int RECOMMEND_SCORE_SPAN = 35;
    // 冷启动用户的 finalScore 是启发式原始分，分布不受 0~1 约束，
    // 这里用指数压缩把它平滑映射到展示分区间，避免高分尾部过度拉开。
    private static final double COLD_START_USER_SCORE_SCALE = 10.0;
    // 热门兜底没有可复用的排序分，因此展示分按最终列表位置给一个稳定梯度：
    // 第一条较高，之后逐项递减，但设置下限，避免尾部看起来“几乎不推荐”。
    private static final double HOT_FALLBACK_NORMALIZED_BASE = 0.70;
    private static final double HOT_FALLBACK_NORMALIZED_STEP = 0.03;
    private static final double HOT_FALLBACK_NORMALIZED_MIN = 0.55;

    private static final String SOURCE_CF = "CF";
    private static final String SOURCE_USER_COLD_START = "COLD_START_USER";
    private static final String SOURCE_COURSE_COLD_START = "COLD_START_COURSE";
    private static final String SOURCE_HOT_FALLBACK = "HOT_FALLBACK";

    @Value("${recommend.new-course.enabled:true}")
    private boolean newCourseRecommendEnabled;
    @Value("${recommend.new-course.inject-limit:3}")
    private int newCourseInjectLimit;
    @Value("${recommend.new-course.max-exposure-ratio:0.30}")
    private double newCourseMaxExposureRatio;

    /**
     * 融合推荐主入口。
     *
     * 执行顺序：
     * 1) 冷启动判定：冷启动用户直接走冷启动推荐，再补全图谱信息。
     * 2) 缓存读取：非冷启动用户先查 Redis，命中直接返回。
     * 3) CF 候选构建：调用 CF 服务获取候选，并取 topN 控制后续图谱计算成本。
     * 4) 图谱 readiness 批量计算：一次性获取每门课可学习性与缺失先修信息。
     * 5) 融合排序：按 CF 归一化分与 readiness 加权得到 finalScore。
     * 6) 图谱字段补全：补知识点、缺失先修、学习路径等可解释信息。
     * 7) 缓存回写并返回。
     */
    @Override
    public HybridRecommendResponseDTO recommend(Long userId) {
        if (coldStartSupportService.isColdStartUser(userId)) {
            String coldCacheKey = RECOMMEND_COLD_START_KEY + userId;
            String coldLockKey = RECOMMEND_COLD_START_LOCK_KEY + userId;
            return getOrBuildWithCache(coldCacheKey, coldLockKey, COLD_START_CACHE_TTL_MINUTES,
                    () -> buildColdStartResponse(userId));
        }

        // 用户转为非冷启动后，及时清理冷启动结果缓存，避免堆积无效 key。
        redisTemplate.delete(RECOMMEND_COLD_START_KEY + userId);

        String cacheKey = RECOMMEND_COURSE_KEY + userId;
        String lockKey = RECOMMEND_COURSE_LOCK_KEY + userId;
        return getOrBuildWithCache(cacheKey, lockKey, RECOMMEND_CACHE_TTL_MINUTES,
                () -> buildRegularResponse(userId));
    }

    private HybridRecommendResponseDTO buildColdStartResponse(Long userId) {
        log.info("User {} hit cold-start recommendation branch", userId);
        List<ColdStartRecommendItemVO> coldStartItems = coldStartRecommendService.recommend(userId,
                COLD_START_LIMIT);
        // 冷启动结果统一转为 HybridRecommendItemDTO，后续走同一套图谱补全逻辑，减少分支差异。
        List<HybridRecommendItemDTO> hybridItems = toColdStartHybridItems(coldStartItems);
        enrichGraphInfo(userId, hybridItems, null);
        return new HybridRecommendResponseDTO(userId, hybridItems);
    }

    /**
     * 非冷启动用户主流程：CF 主链路 + 新课候选增强。
     *
     * 执行要点：
     * 1) 先拿 CF 结果作为主排序依据；
     * 2) 并行准备新课候选（仅在开关开启时）；
     * 3) CF 为空时使用新课候选兜底；
     * 4) CF 不为空时按曝光上限将新课插入固定槽位；
     * 5) 最后统一做图谱字段补全，确保返回结构一致。
     */
    private HybridRecommendResponseDTO buildRegularResponse(Long userId) {
        RecommendResponseDTO cfResp = recommendService.recommend(userId);
        List<RecommendItemDTO> items = cfResp == null ? List.of() : cfResp.getItems();
        List<HybridRecommendItemDTO> newCourseCandidates = newCourseRecommendEnabled
                ? newCourseRecommendService.recommendForRegularUser(userId, NEW_COURSE_CANDIDATE_LIMIT)
                : List.of();
        if (items == null || items.isEmpty()) {
            // CF 无数据时不直接返回空列表，优先使用新课候选保证结果可用。
            List<HybridRecommendItemDTO> fallback = newCourseCandidates.stream()
                    .limit(NEW_COURSE_FALLBACK_LIMIT)
                    .collect(Collectors.toList());
            if (fallback.isEmpty()) {
                fallback = buildHotFallbackItems();
            }
            enrichGraphInfo(userId, fallback, null);
            return new HybridRecommendResponseDTO(userId, fallback);
        }

        List<RecommendItemDTO> topCourses = items.stream()
                .sorted(Comparator.comparing(RecommendItemDTO::getScore).reversed())
                .limit(CANDIDATE_POOL_SIZE)
                .collect(Collectors.toList());

        double min = topCourses.stream()
                .mapToDouble(RecommendItemDTO::getScore)
                .min()
                .orElse(0.0);
        double max = topCourses.stream()
                .mapToDouble(RecommendItemDTO::getScore)
                .max()
                .orElse(1.0);
        double eps = 1e-9;

        List<Long> courseIds = topCourses.stream()
                .map(RecommendItemDTO::getCourseId)
                .collect(Collectors.toList());
        List<CourseReadinessDTO> readinessList = courseGraphRepository.getCourseReadinessBatch(userId, courseIds,
                PREREQUISITE_THRESHOLD);

        Map<Long, CourseReadinessDTO> readinessMap = readinessList.stream()
                .collect(Collectors.toMap(CourseReadinessDTO::getCourseId, r -> r, (a, b) -> a));
        Map<Long, Course> courseSummaryMap = courseService.getRecommendCourseSummaryMapByIds(courseIds);

        List<HybridRecommendItemDTO> hybridItems = topCourses.stream()
                .map(item -> buildHybridBaseItem(item, min, max, eps, courseSummaryMap, readinessMap))
                // 过滤可学习性不足的课程
                // .filter(dto -> dto.getReadiness() == null || dto.getReadiness() >=
                // READINESS_THRESHOLD)
                .sorted(Comparator.comparing(HybridRecommendItemDTO::getFinalScore).reversed())
                .collect(Collectors.toList());

        // 新课注入数量由“绝对上限 + 暴光比例上限”共同约束，避免挤占原有个性化结果。
        int injectLimit = calculateNewCourseInjectLimit(hybridItems.size());
        List<HybridRecommendItemDTO> mergedItems = mergeWithNewCourseCandidates(hybridItems, newCourseCandidates,
                injectLimit);
        enrichGraphInfo(userId, mergedItems, readinessMap);
        return new HybridRecommendResponseDTO(userId, mergedItems);
    }

    /**
     * 通用缓存模板：先读缓存，未命中时通过短锁防击穿后回源构建并写缓存。
     *
     * 行为约定：
     * 1) 命中缓存直接返回；
     * 2) 未拿到锁时短暂轮询等待缓存，尽量复用其他线程刚写入的结果；
     * 3) 等待失败后执行一次回源兜底，避免请求超时；
     * 4) 持锁线程在 finally 中释放锁，避免死锁。
     */
    private HybridRecommendResponseDTO getOrBuildWithCache(String cacheKey, String lockKey, long ttlMinutes,
            Supplier<HybridRecommendResponseDTO> builder) {
        HybridRecommendResponseDTO cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        boolean locked = tryAcquireBuildLock(lockKey);
        if (!locked) {
            HybridRecommendResponseDTO waited = waitForCache(cacheKey);
            if (waited != null) {
                return waited;
            }

            HybridRecommendResponseDTO fallback = builder.get();
            writeCache(cacheKey, fallback, ttlMinutes);
            return fallback;
        }

        try {
            HybridRecommendResponseDTO doubleChecked = readCache(cacheKey);
            if (doubleChecked != null) {
                return doubleChecked;
            }

            HybridRecommendResponseDTO result = builder.get();
            writeCache(cacheKey, result, ttlMinutes);
            return result;
        } finally {
            releaseBuildLock(lockKey);
        }
    }

    /**
     * 读取缓存并兼容 Map 反序列化场景。
     *
     * 这里读取后仍会再次执行一次展示分补齐，看起来像与 writeCache 重复，
     * 但这是刻意保留的“读路径自修复”：
     * 1) 兼容旧缓存：历史缓存里可能根本没有 recommendScore 字段；
     * 2) 兼容反序列化：某些缓存序列化方式会把 DTO 还原成 Map，再转回对象；
     * 3) 兼容规则演进：如果未来展示分公式调整，旧缓存也能在读取时即时按新规则补齐。
     *
     * 换句话说：
     * - writeCache 负责保证“新写入的缓存”和“当前请求的即时返回值”已带展示分；
     * - readCache 负责保证“任何历史缓存对象”在出缓存时都满足当前出参约定。
     *
     * 由于结果列表规模很小，这个补齐成本只是一次轻量遍历，优先保证语义稳定而不是省掉这点计算。
     */
    private HybridRecommendResponseDTO readCache(String cacheKey) {
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached == null) {
            return null;
        }
        if (cached instanceof HybridRecommendResponseDTO dto) {
            fillRecommendScores(dto);
            return dto;
        }
        HybridRecommendResponseDTO dto = objectMapper.convertValue(cached, HybridRecommendResponseDTO.class);
        fillRecommendScores(dto);
        return dto;
    }

    /**
     * 写入缓存，统一使用分钟级 TTL。
     *
     * 写入前先补齐 recommendScore，不只是为了缓存本身，更是为了保证：
     * 当前这次 cache miss 触发的请求，在 writeCache 返回后直接把 result 返回给前端时，
     * 结果对象已经具备完整展示字段，而不是依赖下次“从缓存读出来”时才补齐。
     */
    private void writeCache(String cacheKey, HybridRecommendResponseDTO value, long ttlMinutes) {
        fillRecommendScores(value);
        redisTemplate.opsForValue().set(cacheKey, value, ttlMinutes, TimeUnit.MINUTES);
    }

    /**
     * 获取构建锁（短 TTL），用于降低缓存击穿时的并发回源。
     */
    private boolean tryAcquireBuildLock(String lockKey) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", CACHE_BUILD_LOCK_TTL_SECONDS,
                TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 释放构建锁。
     */
    private void releaseBuildLock(String lockKey) {
        redisTemplate.delete(lockKey);
    }

    /**
     * 在未拿到锁时短暂等待缓存填充，减少重复回源。
     */
    private HybridRecommendResponseDTO waitForCache(String cacheKey) {
        for (int i = 0; i < CACHE_WAIT_RETRY_TIMES; i++) {
            sleepQuietly(CACHE_WAIT_MILLIS);
            HybridRecommendResponseDTO waited = readCache(cacheKey);
            if (waited != null) {
                return waited;
            }
        }
        return null;
    }

    /**
     * 受控睡眠，保留中断标记，避免吞掉线程中断信号。
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 为最终出参补齐统一展示分。
     *
     * 约定：
     * - finalScore 继续只服务后端排序；
     * - recommendScore 统一映射到 60~95，专供前端展示；
     * - 即使缓存中是旧版本对象，也会在读取时即时补齐，无需手工清缓存。
     */
    private void fillRecommendScores(HybridRecommendResponseDTO response) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            return;
        }
        List<HybridRecommendItemDTO> items = response.getItems();
        for (int i = 0; i < items.size(); i++) {
            HybridRecommendItemDTO item = items.get(i);
            if (item == null) {
                continue;
            }
            item.setRecommendScore(toRecommendScore(item, i));
        }
    }

    /**
     * 把单条推荐结果换算成前端展示分。
     *
     * 处理流程分两步：
     * 1) 先按来源把该条结果映射到 0~1 的 normalized 分；
     * 2) 再统一映射到 60~95 整数区间。
     *
     * 这样做的目的，是把“不同推荐链路的内部打分公式”与“统一展示口径”解耦。
     */
    private int toRecommendScore(HybridRecommendItemDTO item, int index) {
        double normalized = normalizeRecommendScore(item, index);
        if (!Double.isFinite(normalized) || normalized < 0.0) {
            normalized = 0.0;
        } else if (normalized > 1.0) {
            normalized = 1.0;
        }
        return (int) Math.round(RECOMMEND_SCORE_BASE + normalized * RECOMMEND_SCORE_SPAN);
    }

    /**
     * 按推荐来源把内部分数压缩到统一的 0~1 区间。
     *
     * 各来源规则说明：
     * - HOT_FALLBACK：热门兜底没有可比较的 finalScore，直接按最终位置给稳定梯度；
     * - COLD_START_USER：冷启动使用启发式原始分，需要指数压缩；
     * - CF / COLD_START_COURSE：这两类当前 finalScore 已按 0~1 设计，可直接裁剪；
     * - 其他未知来源：走 clamp01 兜底，避免新增来源时直接把异常值暴露到前端。
     *
     * 这里不要把不同来源强行改写成相同语义，只做“展示口径统一”，不改变原有业务排序逻辑。
     */
    private double normalizeRecommendScore(HybridRecommendItemDTO item, int index) {
        String source = item.getRecommendSource();
        if (SOURCE_HOT_FALLBACK.equals(source)) {
            return Math.max(HOT_FALLBACK_NORMALIZED_MIN,
                    HOT_FALLBACK_NORMALIZED_BASE - index * HOT_FALLBACK_NORMALIZED_STEP);
        }

        double finalScore = safeFinalScore(item.getFinalScore());
        if (SOURCE_USER_COLD_START.equals(source)) {
            return 1.0 - Math.exp(-finalScore / COLD_START_USER_SCORE_SCALE);
        }
        if (SOURCE_CF.equals(source) || SOURCE_COURSE_COLD_START.equals(source)) {
            return clamp01(finalScore);
        }
        return clamp01(finalScore);
    }

    /**
     * 清洗 finalScore 输入。
     *
     * 展示分不应向前端暴露 null、NaN、Infinity 或负数；
     * 这些情况统一降为 0，对应最终展示下限 60 分。
     */
    private double safeFinalScore(Double finalScore) {
        if (finalScore == null || !Double.isFinite(finalScore) || finalScore < 0.0) {
            return 0.0;
        }
        return finalScore;
    }

    /**
     * 约束到 0~1 区间。
     *
     * 这里单独抽方法，是为了把“展示分归一化”与具体来源规则拆开，
     * 避免后续修改来源分支时重复散落边界处理。
     */
    private double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * 冷启动推荐项 -> 融合推荐项列表。
     *
     * 冷启动结果本身包含基础展示字段与得分，这里统一转换为 HybridRecommendItemDTO，
     * 便于后续复用 enrichGraphInfo 做图谱字段补全。
     */
    private List<HybridRecommendItemDTO> toColdStartHybridItems(List<ColdStartRecommendItemVO> coldStartItems) {
        return coldStartItems == null ? List.of()
                : coldStartItems.stream()
                        .map(this::toHybridRecommendItem)
                        .toList();
    }

    /**
     * 单条冷启动结果转换。
     *
     * 说明：冷启动场景通常没有可靠 CF 分，因此只填充 finalScore 与展示字段，
     * readiness/知识图谱字段在后续补全阶段统一写入。
     */
    private HybridRecommendItemDTO toHybridRecommendItem(ColdStartRecommendItemVO item) {
        HybridRecommendItemDTO dto = new HybridRecommendItemDTO();
        dto.setCourseId(item.getCourseId());
        dto.setTitle(item.getTitle());
        dto.setCoverUrl(item.getCoverUrl());
        dto.setDifficulty(item.getDifficulty());
        dto.setFinalScore(item.getScore());
        dto.setReason(item.getReason());
        dto.setRecommendSource(SOURCE_USER_COLD_START);
        dto.setIsNewCourse(Boolean.FALSE);
        return dto;
    }

    /**
     * 构建非冷启动的基础推荐项（仅负责打分，不负责图谱解释字段）。
     *
     * 融合打分：
     * finalScore = CF_WEIGHT * cfNorm + (1 - CF_WEIGHT) * readiness
     * 其中：
     * - cfNorm：CF 原始分归一化结果，避免不同模型分布导致权重失真。
     * - readiness：课程可学习性，默认值 1.0（缺少图谱数据时不惩罚）。
     */
    private HybridRecommendItemDTO buildHybridBaseItem(RecommendItemDTO item, double min, double max, double eps,
            Map<Long, Course> courseSummaryMap, Map<Long, CourseReadinessDTO> readinessMap) {
        Long courseId = item.getCourseId();
        Course course = courseSummaryMap.get(courseId);
        Double cfScore = item.getScore();

        // 排序层允许对缺失图谱数据使用 1.0 兜底，避免“没有 readiness 数据”的课程被系统性压低。
        // 但 explain 层不会直接复用这个兜底值，而是只根据 readinessDTO 的真实值生成 reason，
        // 防止把“未知”误说成“当前可直接学习”。
        CourseReadinessDTO readinessDTO = readinessMap.get(courseId);
        double readiness = (readinessDTO == null || readinessDTO.getReadiness() == null) ? 1.0
                : readinessDTO.getReadiness();
        double cfNorm = (cfScore - min) / (max - min + eps);
        double finalScore = CF_WEIGHT * cfNorm + (1 - CF_WEIGHT) * readiness;

        HybridRecommendItemDTO dto = new HybridRecommendItemDTO();
        dto.setCourseId(courseId);
        dto.setTitle(course == null ? null : course.getTitle());
        dto.setCoverUrl(course == null ? null : course.getCoverUrl());
        dto.setDifficulty(course == null ? null : course.getDifficulty());
        dto.setCfScore(cfScore);
        dto.setReadiness(readiness);
        dto.setFinalScore(finalScore);
        dto.setReason(buildCfReason(readinessDTO));
        dto.setRecommendSource(SOURCE_CF);
        dto.setIsNewCourse(Boolean.FALSE);
        return dto;
    }

    /**
     * 构建 CF 场景文案。
     *
     * 关键约束：
     * - 这里只看“真实图谱 readiness”；
     * - 如果图谱缺失，文案保持通用推荐说明，不根据排序兜底值 1.0 推断“可直接学习”；
     * - 这样可以同时兼顾排序稳定性和解释语义准确性。
     */
    private String buildCfReason(CourseReadinessDTO readinessDTO) {
        if (readinessDTO == null || readinessDTO.getReadiness() == null) {
            return "根据你的学习行为推荐";
        }
        if (readinessDTO.getReadiness() >= PREREQUISITE_THRESHOLD) {
            return "根据你的学习行为推荐；当前可直接学习";
        }
        return "根据你的学习行为推荐；建议先补齐先修知识";
    }

    /**
     * 构建热门课程兜底结果。
     *
     * 读取策略不是“固定取前 10 个 raw hot ids”，而是分批向后扫描：
     * 1) 从 Redis 热榜读取一段区间；
     * 2) 在 SQL 侧按在线状态过滤课程摘要；
     * 3) 过滤后如果数量不足，则继续扫描下一段；
     * 4) 直到补满、Redis 无更多数据、或达到扫描上限。
     *
     * 这样可以兼容历史热榜里仍残留的无效 courseId，而不要求先执行一次全量清理任务。
     */
    private List<HybridRecommendItemDTO> buildHotFallbackItems() {
        List<HybridRecommendItemDTO> results = new ArrayList<>();
        Set<Long> addedCourseIds = new LinkedHashSet<>();
        int scannedCount = 0;
        while (results.size() < HOT_FALLBACK_LIMIT && scannedCount < HOT_FALLBACK_MAX_SCAN_COUNT) {
            int batchSize = Math.min(HOT_FALLBACK_SCAN_BATCH_SIZE, HOT_FALLBACK_MAX_SCAN_COUNT - scannedCount);
            List<Long> hotCourseIds = learningAnalysisService.getHotCoursesByRange(scannedCount, batchSize);
            if (hotCourseIds == null || hotCourseIds.isEmpty()) {
                break;
            }

            Map<Long, Course> courseSummaryMap = courseService.getOnlineRecommendCourseSummaryMapByIds(hotCourseIds);
            for (Long courseId : hotCourseIds) {
                if (courseId == null || !addedCourseIds.add(courseId)) {
                    continue;
                }
                Course course = courseSummaryMap.get(courseId);
                if (course == null) {
                    continue;
                }
                HybridRecommendItemDTO item = new HybridRecommendItemDTO();
                item.setCourseId(courseId);
                item.setTitle(course.getTitle());
                item.setCoverUrl(course.getCoverUrl());
                item.setDifficulty(course.getDifficulty());
                item.setCfScore(null);
                item.setFinalScore(0.0);
                item.setReason("热门课程兜底：近期较多同学在学");
                item.setRecommendSource(SOURCE_HOT_FALLBACK);
                item.setIsNewCourse(Boolean.FALSE);
                results.add(item);
                if (results.size() >= HOT_FALLBACK_LIMIT) {
                    break;
                }
            }
            // scannedCount 按“已扫描的热榜成员数”推进，而不是按“已返回结果数”推进，
            // 这样下一轮才能真正越过本轮被过滤掉的脏数据，继续向后补齐在线课程。
            scannedCount += hotCourseIds.size();
        }
        return results;
    }

    /**
     * 计算本次结果可注入的新课上限。
     *
     * 规则：
     * - 受配置 `inject-limit` 限制；
     * - 同时受 `max-exposure-ratio` 限制；
     * - ratio > 0 但计算结果为 0 时，允许最少注入 1 条，避免配置有值但永远不生效。
     */
    private int calculateNewCourseInjectLimit(int regularItemSize) {
        if (!newCourseRecommendEnabled) {
            return 0;
        }
        int safeInjectLimit = Math.max(newCourseInjectLimit, 0);
        if (safeInjectLimit == 0 || regularItemSize <= 0) {
            return 0;
        }
        double safeExposureRatio = Math.max(newCourseMaxExposureRatio, 0.0);
        int byRatio = (int) Math.floor(regularItemSize * safeExposureRatio);
        if (byRatio <= 0 && safeExposureRatio > 0) {
            byRatio = 1;
        }
        return Math.min(safeInjectLimit, byRatio);
    }

    /**
     * 将新课候选插入常规推荐结果。
     *
     * 设计考虑：
     * - 使用固定插槽保证新课稳定曝光位置；
     * - 超出插槽后追加在尾部，避免复杂重排；
     * - 按 courseId 去重，避免同一课程在 CF 与新课候选重复出现。
     */
    private List<HybridRecommendItemDTO> mergeWithNewCourseCandidates(List<HybridRecommendItemDTO> regularItems,
            List<HybridRecommendItemDTO> newCourseCandidates, int injectLimit) {
        if (regularItems == null || regularItems.isEmpty()) {
            return regularItems == null ? List.of() : regularItems;
        }
        if (newCourseCandidates == null || newCourseCandidates.isEmpty() || injectLimit <= 0) {
            return regularItems;
        }

        List<HybridRecommendItemDTO> merged = new ArrayList<>(regularItems);
        Set<Long> seenCourseIds = merged.stream()
                .map(HybridRecommendItemDTO::getCourseId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int injected = 0;
        for (HybridRecommendItemDTO candidate : newCourseCandidates) {
            Long courseId = candidate.getCourseId();
            if (courseId == null || !seenCourseIds.add(courseId)) {
                continue;
            }
            int targetIndex;
            if (injected < NEW_COURSE_INJECT_SLOTS.length && NEW_COURSE_INJECT_SLOTS[injected] < merged.size()) {
                // 优先使用预留插槽，仅在插槽位于当前列表内部时插入，避免与尾部追加逻辑重叠。
                targetIndex = NEW_COURSE_INJECT_SLOTS[injected];
            } else {
                targetIndex = merged.size();
            }
            merged.add(targetIndex, candidate);
            injected++;
            if (injected >= injectLimit) {
                break;
            }
        }
        return merged;
    }

    /**
     * 图谱补全共用逻辑：为冷启动与非冷启动结果统一填充可解释字段。
     *
     * 补全内容：
     * 1) readiness：可学习性（如果基础项尚未写入）。
     * 2) missingPrerequisitesMastery：缺失先修及掌握度。
     * 3) knowledgePoints：课程知识点概览。
     * 4) learningPaths：建议补齐路径（按先修关系展开）。
     *
     * 性能注意：
     * - 非冷启动主流程可复用已算好的 readinessMap；若仅覆盖部分课程，会自动补查缺口。
     * - 冷启动分支传 null 时，在本方法内按 courseIds 批量查询。
     */
    private void enrichGraphInfo(Long userId, List<HybridRecommendItemDTO> items,
            Map<Long, CourseReadinessDTO> readinessMap) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<Long> courseIds = items.stream()
                .map(HybridRecommendItemDTO::getCourseId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (courseIds.isEmpty()) {
            return;
        }

        // 允许上游复用 readinessMap。
        // 若传入的是“部分 map”（例如仅覆盖 CF 候选），则补查缺失课程，避免新课注入后解释字段降级。
        Map<Long, CourseReadinessDTO> effectiveReadinessMap = readinessMap == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(readinessMap);
        List<Long> missingReadinessCourseIds = courseIds.stream()
                .filter(id -> !effectiveReadinessMap.containsKey(id))
                .toList();
        if (!missingReadinessCourseIds.isEmpty()) {
            List<CourseReadinessDTO> readinessList = courseGraphRepository.getCourseReadinessBatch(
                    userId,
                    missingReadinessCourseIds,
                    PREREQUISITE_THRESHOLD);
            if (readinessList != null && !readinessList.isEmpty()) {
                Map<Long, CourseReadinessDTO> fetchedReadinessMap = readinessList.stream()
                        .filter(r -> r != null && r.getCourseId() != null)
                        .collect(Collectors.toMap(CourseReadinessDTO::getCourseId, r -> r, (a, b) -> a));
                effectiveReadinessMap.putAll(fetchedReadinessMap);
            }
        }
        Map<Long, List<KnowledgeVO>> knowledgePointsMap = loadCourseKnowledgePointsMap(courseIds);
        Map<Long, List<List<KnowledgeVO>>> learningPathsMap = loadLearningPathsMap(userId, courseIds,
                PREREQUISITE_THRESHOLD, LEARNING_PATH_LIMIT_PER_COURSE);

        // 逐课程填充图谱解释信息。
        for (HybridRecommendItemDTO item : items) {
            Long courseId = item.getCourseId();
            if (courseId == null) {
                // courseId 缺失时保持空集合，避免前端空指针。
                item.setKnowledgePoints(List.of());
                item.setMissingPrerequisitesMastery(List.of());
                item.setLearningPaths(List.of());
                continue;
            }

            CourseReadinessDTO readinessDTO = effectiveReadinessMap.get(courseId);
            if (item.getReadiness() == null) {
                double readiness = (readinessDTO == null || readinessDTO.getReadiness() == null) ? 1.0
                        : readinessDTO.getReadiness();
                item.setReadiness(readiness);
            }

            // 缺失先修：用于提示“还差哪些知识点”。
            List<KnowledgeMasteryVO> missing = (readinessDTO == null || readinessDTO.getMissing() == null) ? List.of()
                    : readinessDTO.getMissing();
            item.setMissingPrerequisitesMastery(missing);

            // 课程知识点概览：用于展示课程覆盖内容。
            item.setKnowledgePoints(knowledgePointsMap.getOrDefault(courseId, List.of()));

            // 学习路径：用于展示“从当前水平到可学习该课程”的推荐补齐路径。
            item.setLearningPaths(learningPathsMap.getOrDefault(courseId, List.of()));
        }
    }

    /**
     * 批量加载课程知识点，避免推荐列表逐课程访问 Neo4j。
     */
    private Map<Long, List<KnowledgeVO>> loadCourseKnowledgePointsMap(List<Long> courseIds) {
        List<CourseKnowledgePointDTO> rows = courseGraphRepository.findCourseKnowledgePointsBatch(courseIds);
        Map<Long, List<KnowledgeVO>> map = new LinkedHashMap<>();
        if (rows == null || rows.isEmpty()) {
            return map;
        }
        for (CourseKnowledgePointDTO row : rows) {
            if (row == null || row.getCourseId() == null || row.getId() == null) {
                continue;
            }
            map.computeIfAbsent(row.getCourseId(), k -> new ArrayList<>())
                    .add(new KnowledgeVO(row.getId(), row.getName(), row.getDifficulty()));
        }
        return map;
    }

    /**
     * 批量计算学习路径（包含掌握度过滤）。
     *
     * 查询意图与旧的单课程查询保持一致，但以本次推荐 courseIds 为批量边界：
     * 1) 从课程知识点出发，沿 PRE_REQUIRES 关系回溯 1..3 层先修链。
     * 2) 过滤掉“用户已掌握”的节点，仅保留未达阈值的先修链路。
     * 3) 去掉被更长路径完全前缀覆盖的短路径，减少冗余展示。
     * 4) 每门课按路径长度/起点难度排序后最多保留 limit 条。
     */
    private Map<Long, List<List<KnowledgeVO>>> loadLearningPathsMap(Long userId, List<Long> courseIds,
            double threshold, int limit) {
        if (userId == null || courseIds == null || courseIds.isEmpty() || limit <= 0) {
            return Map.of();
        }

        String cypher = """
                    MATCH (u:User {id: $userId})
                    UNWIND $courseIds AS courseId
                    CALL {
                        WITH u, courseId
                        MATCH (c:Course {id: courseId})-[:HAS_KP]->(k:Knowledge)
                        MATCH p = (k)-[:PRE_REQUIRES*1..3]->(pre:Knowledge)
                        WHERE ALL(n IN nodes(p)[1..] WHERE
                            coalesce( [(u)-[m:MASTERED]->(n) | m.score][0], 0.0 ) < $threshold
                        )
                        WITH reverse(nodes(p)) AS nodes
                        WITH collect(nodes) AS nodePaths
                        UNWIND nodePaths AS nodes
                        WITH nodes, nodePaths
                        WHERE NOT any(other IN nodePaths WHERE
                            size(other) > size(nodes)
                            AND all(i IN range(0, size(nodes) - 1) WHERE nodes[i].id = other[i].id)
                        )
                        WITH nodes
                        ORDER BY size(nodes), nodes[0].difficulty
                        LIMIT $limit
                        RETURN collect([n IN nodes | {id:n.id, name:n.name, difficulty:n.difficulty}]) AS paths
                    }
                    RETURN courseId, paths
                """;

        Map<Long, List<List<KnowledgeVO>>> map = new LinkedHashMap<>();
        neo4jClient.query(cypher)
                .bindAll(java.util.Map.of(
                        "userId", userId,
                        "courseIds", courseIds,
                        "threshold", threshold,
                        "limit", limit))
                .fetch()
                .all()
                .forEach(row -> {
                    Long courseId = parseLong(row.get("courseId"));
                    if (courseId == null) {
                        return;
                    }
                    List<List<KnowledgeVO>> paths = parseKnowledgePaths(row.get("paths"));
                    if (!paths.isEmpty()) {
                        map.put(courseId, paths);
                    }
                });
        return map;
    }

    private List<List<KnowledgeVO>> parseKnowledgePaths(Object pathsObj) {
        if (!(pathsObj instanceof List<?> list)) {
            return List.of();
        }
        List<List<KnowledgeVO>> paths = new ArrayList<>();
        for (Object pathObj : list) {
            List<KnowledgeVO> path = parseKnowledgePath(pathObj);
            if (!path.isEmpty()) {
                paths.add(path);
            }
        }
        return paths;
    }

    private List<KnowledgeVO> parseKnowledgePath(Object pathObj) {
        // SDN Neo4jClient 返回的数据通常是 List<Map>，这里做类型防御解析。
        if (!(pathObj instanceof List<?> list)) {
            return List.of();
        }
        List<KnowledgeVO> path = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            path.add(new KnowledgeVO(
                    parseLong(m.get("id")),
                    parseString(m.get("name")),
                    parseInteger(m.get("difficulty"))));
        }
        return path;
    }

    private Long parseLong(Object value) {
        return value instanceof Number num ? num.longValue() : null;
    }

    private Integer parseInteger(Object value) {
        return value instanceof Number num ? num.intValue() : null;
    }

    private String parseString(Object value) {
        return value instanceof String text ? text : null;
    }

}
