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
import com.sy.course_system.dto.recommend.CourseReadinessDTO;
import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;
import com.sy.course_system.dto.recommend.KnowledgeBriefDTO;
import com.sy.course_system.dto.recommend.KnowledgeMasteryDTO;
import com.sy.course_system.dto.recommend.RecommendItemDTO;
import com.sy.course_system.dto.recommend.RecommendResponseDTO;
import com.sy.course_system.graph.node.KnowledgeNode;
import com.sy.course_system.repository.CourseGraphRepository;
import com.sy.course_system.service.ColdStartRecommendService;
import com.sy.course_system.service.ColdStartSupportService;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.HybridRecommendService;
import com.sy.course_system.service.NewCourseRecommendService;
import com.sy.course_system.service.RecommendService;
import com.sy.course_system.vo.ColdStartRecommendItemVO;

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

    // 融合分中 CF 的权重（readiness 权重为 1 - CF_WEIGHT）
    private static final double CF_WEIGHT = 0.7;

    // 可学习性过滤阈值（当前过滤逻辑暂未启用，保留用于策略开关）
    @SuppressWarnings("unused")
    private static final double READINESS_THRESHOLD = 0.4;

    private static final String SOURCE_CF = "CF";
    private static final String SOURCE_USER_COLD_START = "COLD_START_USER";

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
        Map<Long, String> courseTitleMap = courseService.getCourseTitleMapByIds(courseIds);

        List<HybridRecommendItemDTO> hybridItems = topCourses.stream()
                .map(item -> buildHybridBaseItem(item, min, max, eps, courseTitleMap, readinessMap))
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
     */
    private HybridRecommendResponseDTO readCache(String cacheKey) {
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached == null) {
            return null;
        }
        if (cached instanceof HybridRecommendResponseDTO dto) {
            return dto;
        }
        return objectMapper.convertValue(cached, HybridRecommendResponseDTO.class);
    }

    /**
     * 写入缓存，统一使用分钟级 TTL。
     */
    private void writeCache(String cacheKey, HybridRecommendResponseDTO value, long ttlMinutes) {
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
            Map<Long, String> courseTitleMap, Map<Long, CourseReadinessDTO> readinessMap) {
        Long courseId = item.getCourseId();
        String title = courseTitleMap.get(courseId);
        Double cfScore = item.getScore();

        // 基于 readiness 计算融合分
        CourseReadinessDTO readinessDTO = readinessMap.get(courseId);
        double readiness = (readinessDTO == null || readinessDTO.getReadiness() == null) ? 1.0
                : readinessDTO.getReadiness();
        double cfNorm = (cfScore - min) / (max - min + eps);
        double finalScore = CF_WEIGHT * cfNorm + (1 - CF_WEIGHT) * readiness;

        HybridRecommendItemDTO dto = new HybridRecommendItemDTO();
        dto.setCourseId(courseId);
        dto.setTitle(title);
        dto.setCfScore(cfScore);
        dto.setReadiness(readiness);
        dto.setFinalScore(finalScore);
        dto.setRecommendSource(SOURCE_CF);
        dto.setIsNewCourse(Boolean.FALSE);
        return dto;
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
            List<KnowledgeMasteryDTO> missing = (readinessDTO == null || readinessDTO.getMissing() == null) ? List.of()
                    : readinessDTO.getMissing();
            item.setMissingPrerequisitesMastery(missing);

            // 课程知识点概览：用于展示课程覆盖内容。
            List<KnowledgeNode> kps = courseGraphRepository.findCourseKnowledgePoints(courseId);
            item.setKnowledgePoints(convertBrief(kps));

            // 学习路径：用于展示“从当前水平到可学习该课程”的推荐补齐路径。
            List<List<KnowledgeNode>> paths = findLearningPathsRawViaClient(userId, courseId,
                    PREREQUISITE_THRESHOLD, 5);
            List<List<KnowledgeBriefDTO>> pathDTOs = paths.stream()
                    .map(this::convertBrief)
                    .collect(Collectors.toList());
            item.setLearningPaths(pathDTOs);
        }
    }

    /**
     * 轻量知识点转换：KnowledgeNode -> KnowledgeBriefDTO。
     *
     * 仅保留前端需要的 id/name/difficulty，避免将图数据库实体直接暴露到接口层。
     */
    private List<KnowledgeBriefDTO> convertBrief(List<KnowledgeNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        return nodes.stream()
                .map(k -> new KnowledgeBriefDTO(
                        k.getId(),
                        k.getName(),
                        k.getDifficulty()))
                .collect(Collectors.toList());
    }

    /**
     * 通过 Neo4jClient 原生 Cypher 计算学习路径（包含掌握度过滤）。
     *
     * 查询意图：
     * 1) 从课程知识点出发，沿 PRE_REQUIRES 关系回溯 1..3 层先修链。
     * 2) 过滤掉“用户已掌握”的节点，仅保留未达阈值的先修链路。
     * 3) 去掉被更长路径完全前缀覆盖的短路径，减少冗余展示。
     * 4) 返回节点基础字段并按路径长度/起点难度排序。
     */
    private List<List<KnowledgeNode>> findLearningPathsRawViaClient(Long userId, Long courseId, double threshold,
            int limit) {

        // Cypher 说明：
        // - MATCH (c)-[:HAS_KP]->(k)：以课程知识点作为“目标知识”。
        // - MATCH p=(k)-[:PRE_REQUIRES*1..3]->(pre)：反向追溯先修链，最多 3 层。
        // - WHERE ALL(... < $threshold)：仅保留链路上未掌握节点。
        // - reverse(nodes(p))：将路径顺序改为“基础 -> 目标”，更符合学习顺序。
        // - NOT any(other ...)：移除被更长路径前缀覆盖的短路径，减少重复建议。
        String cypher = """
                    MATCH (u:User {id: $userId})
                    WITH u
                    MATCH (c:Course {id: $courseId})-[:HAS_KP]->(k:Knowledge)
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
                    RETURN [n IN nodes | {id:n.id, name:n.name, difficulty:n.difficulty}] AS path
                    ORDER BY size(nodes), nodes[0].difficulty
                    LIMIT $limit
                """;

        return neo4jClient.query(cypher)
                .bindAll(java.util.Map.of(
                        "userId", userId,
                        "courseId", courseId,
                        "threshold", threshold,
                        "limit", limit))
                .fetch()
                .all()
                .stream()
                .map(row -> {
                    Object pathObj = row.get("path");
                    // SDN Neo4jClient 返回的数据通常是 List<Map>，这里做类型防御解析。
                    if (!(pathObj instanceof List<?> list))
                        return List.<KnowledgeNode>of();
                    List<KnowledgeNode> path = new java.util.ArrayList<>();
                    for (Object o : list) {
                        if (!(o instanceof java.util.Map<?, ?> m))
                            continue;
                        KnowledgeNode k = new KnowledgeNode();
                        Object id = m.get("id");
                        if (id instanceof Number num)
                            k.setId(num.longValue());
                        k.setName((String) m.get("name"));
                        Object diff = m.get("difficulty");
                        if (diff instanceof Number num)
                            k.setDifficulty(num.intValue());
                        path.add(k);
                    }
                    return path;
                })
                .filter(p -> !p.isEmpty())
                .toList();
    }

}
