package com.sy.course_system.service.impl;

import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.sy.course_system.client.CfRecommendClient;
import com.sy.course_system.common.util.ConcurrentUtils;
import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.recommend.CourseReadinessDTO;
import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;
import com.sy.course_system.dto.recommend.RecommendItemDTO;
import com.sy.course_system.dto.recommend.RecommendResponseDTO;
import com.sy.course_system.entity.Course;
import com.sy.course_system.recommend.HotFallbackRecommendService;
import com.sy.course_system.recommend.RecommendSource;
import com.sy.course_system.recommend.NewCourseInjector;
import com.sy.course_system.recommend.RecommendGraphEnricher;
import com.sy.course_system.recommend.RecommendResultCache;
import com.sy.course_system.repository.CourseGraphRepository;
import com.sy.course_system.service.ColdStartRecommendService;
import com.sy.course_system.service.ColdStartSupportService;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.HybridRecommendService;
import com.sy.course_system.service.NewCourseRecommendService;
import com.sy.course_system.service.UserCourseService;
import com.sy.course_system.vo.ColdStartRecommendItemVO;

/**
 * 混合推荐服务编排：负责冷启动判定、CF 链路编排、fallback 选择，
 * 具体组件（缓存、展示分、新课注入、热门兜底、图谱补全）已拆分至独立组件。
 */
@Service
public class HybridRecommendServiceImpl implements HybridRecommendService {

    private static final Logger log = LoggerFactory.getLogger(HybridRecommendServiceImpl.class);

    private final CfRecommendClient cfRecommendClient;
    private final CourseGraphRepository courseGraphRepository;
    private final CourseService courseService;
    private final ColdStartSupportService coldStartSupportService;
    private final ColdStartRecommendService coldStartRecommendService;
    private final NewCourseRecommendService newCourseRecommendService;
    private final UserCourseService userCourseService;
    private final NewCourseInjector newCourseInjector;
    private final RecommendResultCache recommendResultCache;
    private final HotFallbackRecommendService hotFallbackRecommendService;
    private final RecommendGraphEnricher recommendGraphEnricher;
    private final RecommendProperties recommendProperties;

    private static final String RECOMMEND_COURSE_KEY = "recommend:user:";
    private static final String RECOMMEND_COLD_START_KEY = "recommend:cold:user:";
    private static final String RECOMMEND_COURSE_LOCK_KEY = "recommend:lock:user:";
    private static final String RECOMMEND_COLD_START_LOCK_KEY = "recommend:cold:lock:user:";

    private static final String ASYNC_INTERRUPTED_MSG = "推荐异步执行被中断";
    private static final String GRAPH_ASYNC_INTERRUPTED_MSG = "图谱补全异步执行被中断";

    private final Executor recommendTaskExecutor;

    public HybridRecommendServiceImpl(CfRecommendClient cfRecommendClient,
            CourseGraphRepository courseGraphRepository,
            CourseService courseService,
            ColdStartSupportService coldStartSupportService,
            ColdStartRecommendService coldStartRecommendService,
            NewCourseRecommendService newCourseRecommendService,
            UserCourseService userCourseService,
            NewCourseInjector newCourseInjector,
            RecommendResultCache recommendResultCache,
            HotFallbackRecommendService hotFallbackRecommendService,
            RecommendGraphEnricher recommendGraphEnricher,
            RecommendProperties recommendProperties,
            @Qualifier("recommendTaskExecutor") Executor recommendTaskExecutor) {
        this.cfRecommendClient = cfRecommendClient;
        this.courseGraphRepository = courseGraphRepository;
        this.courseService = courseService;
        this.coldStartSupportService = coldStartSupportService;
        this.coldStartRecommendService = coldStartRecommendService;
        this.newCourseRecommendService = newCourseRecommendService;
        this.userCourseService = userCourseService;
        this.newCourseInjector = newCourseInjector;
        this.recommendResultCache = recommendResultCache;
        this.hotFallbackRecommendService = hotFallbackRecommendService;
        this.recommendGraphEnricher = recommendGraphEnricher;
        this.recommendProperties = recommendProperties;
        this.recommendTaskExecutor = recommendTaskExecutor;
    }

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
            return recommendResultCache.getOrBuildWithCache(coldCacheKey, coldLockKey,
                    recommendProperties.getCache().getColdStartTtlMinutes(),
                    () -> buildColdStartResponse(userId));
        }

        // 用户转为非冷启动后，及时清理冷启动结果缓存，避免堆积无效 key。
        recommendResultCache.delete(RECOMMEND_COLD_START_KEY + userId);

        String cacheKey = RECOMMEND_COURSE_KEY + userId;
        String lockKey = RECOMMEND_COURSE_LOCK_KEY + userId;
        return recommendResultCache.getOrBuildWithCache(cacheKey, lockKey,
                recommendProperties.getCache().getRegularTtlMinutes(),
                () -> buildRegularResponse(userId));
    }

    private HybridRecommendResponseDTO buildColdStartResponse(Long userId) {
        log.info("User {} hit cold-start recommendation branch", userId);
        List<ColdStartRecommendItemVO> coldStartItems = coldStartRecommendService.recommend(userId,
                recommendProperties.getRegular().getColdStartLimit());
        // 冷启动结果统一转为 HybridRecommendItemDTO，后续走同一套图谱补全逻辑，减少分支差异。
        List<HybridRecommendItemDTO> hybridItems = toColdStartHybridItems(coldStartItems);
        recommendGraphEnricher.enrichGraphInfo(userId, hybridItems, null);
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
        boolean asyncEnabled = recommendProperties.getAsync().isEnabled();
        int newCourseCandidateLimit = Math.max(0,
                recommendProperties.getNewCourse().getRegularCandidateLimit());
        boolean loadNewCourses = newCourseInjector.isEnabled() && newCourseCandidateLimit > 0;
        RecommendResponseDTO cfResp;
        List<HybridRecommendItemDTO> newCourseCandidates;

        // 异步开关开启且新课开关也开启时，CF 与新课候选并行执行，减少串行 IO 等待。
        if (asyncEnabled && loadNewCourses) {
            CompletableFuture<RecommendResponseDTO> cfFuture = CompletableFuture.supplyAsync(
                    () -> cfRecommendClient.recommend(userId), recommendTaskExecutor);
            CompletableFuture<List<HybridRecommendItemDTO>> ncFuture = CompletableFuture.supplyAsync(
                    () -> newCourseRecommendService.recommendForRegularUser(userId,
                            newCourseCandidateLimit),
                    recommendTaskExecutor);
            cfResp = ConcurrentUtils.await(cfFuture, ASYNC_INTERRUPTED_MSG);
            newCourseCandidates = ConcurrentUtils.await(ncFuture, ASYNC_INTERRUPTED_MSG);
        } else {
            cfResp = cfRecommendClient.recommend(userId);
            newCourseCandidates = loadNewCourses
                    ? newCourseRecommendService.recommendForRegularUser(userId,
                            newCourseCandidateLimit)
                    : List.of();
        }

        List<RecommendItemDTO> items = cfResp == null ? List.of() : cfResp.getItems();
        if (items == null || items.isEmpty()) {
            return buildFallbackResponse(userId, newCourseCandidates);
        }

        List<RecommendItemDTO> sortedItems = items.stream()
                .filter(it -> it.getCourseId() != null && it.getScore() != null)
                .sorted(Comparator.comparing(RecommendItemDTO::getScore).reversed())
                .toList();

        List<Long> allCfCourseIds = sortedItems.stream()
                .map(RecommendItemDTO::getCourseId)
                .distinct()
                .toList();

        Map<Long, Course> courseSummaryMap;
        Set<Long> selectedCourseIds;
        if (asyncEnabled) {
            CompletableFuture<Map<Long, Course>> summaryFuture = CompletableFuture.supplyAsync(
                    () -> courseService.getRecommendCourseSummaryMapByIds(allCfCourseIds),
                    recommendTaskExecutor);
            CompletableFuture<Set<Long>> selectedFuture = CompletableFuture.supplyAsync(
                    () -> new HashSet<>(userCourseService.listSelectedCourseIds(userId,
                            allCfCourseIds)),
                    recommendTaskExecutor);
            ConcurrentUtils.awaitAll(GRAPH_ASYNC_INTERRUPTED_MSG, summaryFuture, selectedFuture);
            courseSummaryMap = summaryFuture.getNow(Map.of());
            selectedCourseIds = selectedFuture.getNow(Set.of());
        } else {
            courseSummaryMap = courseService.getRecommendCourseSummaryMapByIds(allCfCourseIds);
            selectedCourseIds = new HashSet<>(userCourseService.listSelectedCourseIds(userId, allCfCourseIds));
        }

        List<RecommendItemDTO> filteredItems = sortedItems.stream()
                .filter(it -> isCourseAvailable(it.getCourseId(), courseSummaryMap, selectedCourseIds))
                .toList();

        if (filteredItems.isEmpty()) {
            return buildFallbackResponse(userId, newCourseCandidates);
        }

        List<RecommendItemDTO> topCourses = filteredItems.stream()
                .limit(Math.max(1, recommendProperties.getRegular().getCandidatePoolSize()))
                .toList();

        DoubleSummaryStatistics stats = topCourses.stream()
                .mapToDouble(RecommendItemDTO::getScore)
                .summaryStatistics();
        double min = stats.getMin();
        double max = stats.getMax();
        double eps = 1e-9;

        List<Long> topCourseIds = topCourses.stream()
                .map(RecommendItemDTO::getCourseId)
                .toList();

        Map<Long, CourseReadinessDTO> readinessMap = recommendGraphEnricher.toReadinessMap(
                courseGraphRepository.getCourseReadinessBatch(
                        userId, topCourseIds, recommendProperties.getGraph().getPrerequisiteThreshold()));

        List<HybridRecommendItemDTO> hybridItems = topCourses.stream()
                .map(item -> buildHybridBaseItem(item, min, max, eps, courseSummaryMap, readinessMap))
                .sorted(Comparator.comparing(HybridRecommendItemDTO::getFinalScore).reversed())
                .toList();

        int injectLimit = newCourseInjector.calculateInjectLimit(hybridItems.size());
        List<HybridRecommendItemDTO> mergedItems = newCourseInjector.merge(hybridItems, newCourseCandidates,
                injectLimit);
        recommendGraphEnricher.enrichGraphInfo(userId, mergedItems, readinessMap);
        return new HybridRecommendResponseDTO(userId, mergedItems);
    }

    private static boolean isCourseAvailable(Long courseId, Map<Long, Course> courseSummaryMap,
            Set<Long> selectedCourseIds) {
        return courseSummaryMap.containsKey(courseId) && !selectedCourseIds.contains(courseId);
    }

    private HybridRecommendResponseDTO buildFallbackResponse(Long userId,
            List<HybridRecommendItemDTO> newCourseCandidates) {
        List<HybridRecommendItemDTO> fallback = newCourseCandidates.stream()
                .limit(Math.max(0, recommendProperties.getNewCourse().getFallbackLimit()))
                .toList();
        if (fallback.isEmpty()) {
            fallback = hotFallbackRecommendService.buildHotFallbackItems();
        }
        recommendGraphEnricher.enrichGraphInfo(userId, fallback, null);
        return new HybridRecommendResponseDTO(userId, fallback);
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
        dto.setRecommendSource(RecommendSource.COLD_START_USER.code());
        dto.setIsNewCourse(Boolean.FALSE);
        return dto;
    }

    /**
     * 构建非冷启动的基础推荐项（仅负责打分，不负责图谱解释字段）。
     *
     * 融合打分：
     * finalScore = cfWeight * cfNorm + (1 - cfWeight) * readiness
     * 其中：
     * - cfNorm：CF 原始分归一化结果，避免不同模型分布导致权重失真。
     * - readiness：课程可学习性，默认值 1.0（缺少图谱数据时不惩罚）。
     */
    private HybridRecommendItemDTO buildHybridBaseItem(RecommendItemDTO item, double min, double max, double eps,
            Map<Long, Course> courseSummaryMap, Map<Long, CourseReadinessDTO> readinessMap) {
        Long courseId = item.getCourseId();
        Course course = courseSummaryMap.get(courseId);
        Double cfScore = item.getScore();

        // 排序层允许对缺失图谱数据使用 1.0 兜底，避免"没有 readiness 数据"的课程被系统性压低。
        // 但 explain 层不会直接复用这个兜底值，而是只根据 readinessDTO 的真实值生成 reason，
        // 防止把"未知"误说成"当前可直接学习"。
        CourseReadinessDTO readinessDTO = readinessMap.get(courseId);
        double readiness = (readinessDTO == null || readinessDTO.getReadiness() == null) ? 1.0
                : readinessDTO.getReadiness();
        double cfNorm = (cfScore - min) / (max - min + eps);
        double cfWeight = recommendProperties.getRegular().getCfWeight();
        double finalScore = cfWeight * cfNorm + (1 - cfWeight) * readiness;

        HybridRecommendItemDTO dto = new HybridRecommendItemDTO();
        dto.setCourseId(courseId);
        dto.setTitle(course == null ? null : course.getTitle());
        dto.setCoverUrl(course == null ? null : course.getCoverUrl());
        dto.setDifficulty(course == null ? null : course.getDifficulty());
        dto.setCfScore(cfScore);
        dto.setReadiness(readiness);
        dto.setFinalScore(finalScore);
        dto.setReason(buildCfReason(readinessDTO));
        dto.setRecommendSource(RecommendSource.CF.code());
        dto.setIsNewCourse(Boolean.FALSE);
        return dto;
    }

    /**
     * 构建 CF 场景文案。
     *
     * 关键约束：
     * - 这里只看"真实图谱 readiness"；
     * - 如果图谱缺失，文案保持通用推荐说明，不根据排序兜底值 1.0 推断"可直接学习"；
     * - 这样可以同时兼顾排序稳定性和解释语义准确性。
     */
    private String buildCfReason(CourseReadinessDTO readinessDTO) {
        if (readinessDTO == null || readinessDTO.getReadiness() == null) {
            return "根据你的学习行为推荐";
        }
        if (readinessDTO.getReadiness() >= recommendProperties.getGraph().getPrerequisiteThreshold()) {
            return "根据你的学习行为推荐；当前可直接学习";
        }
        return "根据你的学习行为推荐；建议先补齐先修知识";
    }

}
