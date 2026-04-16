package com.sy.course_system.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    // Redis 键前缀：最终 key 形如 recommend:user:{userId}
    private static final String RECOMMEND_COURSE_KEY = "recommend:user:";
    private static final String RECOMMEND_COLD_START_KEY = "recommend:cold:user:";
    private static final long COLD_START_CACHE_TTL_MINUTES = 10L;

    // 冷启动返回条数：控制初始曝光规模，避免冷启动阶段结果过多
    private static final int COLD_START_LIMIT = 10;

    // CF 候选池上限：先截断再做图谱计算，减少 Neo4j 批量查询压力
    private static final int CANDIDATE_POOL_SIZE = 20;

    // 先修掌握阈值：小于该值的知识点视为“尚未掌握”，会进入缺失先修或补齐路径
    private static final double PREREQUISITE_THRESHOLD = 0.7;

    // 融合分中 CF 的权重（readiness 权重为 1 - CF_WEIGHT）
    private static final double CF_WEIGHT = 0.7;

    // 可学习性过滤阈值（当前过滤逻辑暂未启用，保留用于策略开关）
    @SuppressWarnings("unused")
    private static final double READINESS_THRESHOLD = 0.4;

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
        // 冷启动分支：
        // - 不依赖用户历史行为，直接使用冷启动策略产生推荐。
        // - 然后复用 enrichGraphInfo，保证冷启动与非冷启动返回字段结构一致。
        if (coldStartSupportService.isColdStartUser(userId)) {
            String coldCacheKey = RECOMMEND_COLD_START_KEY + userId;
            Object coldCached = redisTemplate.opsForValue().get(coldCacheKey);
            if (coldCached != null) {
                if (coldCached instanceof HybridRecommendResponseDTO dto) {
                    return dto;
                }
                HybridRecommendResponseDTO dto = objectMapper.convertValue(coldCached, HybridRecommendResponseDTO.class);
                if (dto != null) {
                    return dto;
                }
            }

            log.info("User {} hit cold-start recommendation branch", userId);
            List<ColdStartRecommendItemVO> coldStartItems = coldStartRecommendService.recommend(userId,
                    COLD_START_LIMIT);
            List<HybridRecommendItemDTO> hybridItems = toColdStartHybridItems(coldStartItems);
            enrichGraphInfo(userId, hybridItems, null);
            HybridRecommendResponseDTO coldResult = new HybridRecommendResponseDTO(userId, hybridItems);
            redisTemplate.opsForValue().set(coldCacheKey, coldResult, COLD_START_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            return coldResult;
        }

        String cacheKey = RECOMMEND_COURSE_KEY + userId;

        // 0) 缓存优先：命中则直接返回，降低 CF/Neo4j 计算压力与接口时延。
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (cached instanceof HybridRecommendResponseDTO dto) {
                return dto;
            }
            // 兼容 Redis 反序列化为 Map 的情况（例如序列化策略变更/跨环境读写）。
            HybridRecommendResponseDTO dto = objectMapper.convertValue(cached, HybridRecommendResponseDTO.class);
            if (dto != null) {
                return dto;
            }
        }

        // 1) 调用 CF 推荐服务。
        // 这里假设 recommendService.recommend(userId) 返回非 null，items 可能为空。
        RecommendResponseDTO cfResp = recommendService.recommend(userId);

        List<RecommendItemDTO> items = cfResp.getItems();
        if (items == null || items.isEmpty()) {
            // CF 无候选时返回空列表，同时写缓存避免短时间内重复计算。
            HybridRecommendResponseDTO empty = new HybridRecommendResponseDTO(userId, List.of());
            redisTemplate.opsForValue().set(cacheKey, empty);
            return empty;
        }

        // 2) 取 topN 候选池。
        // 目的：将“重计算”的图谱环节限制在更小集合中，避免对长尾候选做不必要计算。
        List<RecommendItemDTO> topCourses = items.stream()
                .sorted(Comparator.comparing(RecommendItemDTO::getScore).reversed())
                .limit(CANDIDATE_POOL_SIZE)
                .collect(Collectors.toList());

        // 3) 计算 CF 归一化参数。
        // 归一化公式：cfNorm = (cfScore - min) / (max - min + eps)
        // eps 避免 max == min 时分母为 0。
        double min = topCourses.stream()
                .mapToDouble(RecommendItemDTO::getScore)
                .min()
                .orElse(0.0);
        double max = topCourses.stream()
                .mapToDouble(RecommendItemDTO::getScore)
                .max()
                .orElse(1.0);
        double eps = 1e-9;

        // 4) 批量计算 readiness 与缺失先修信息。
        // 这里通过 courseIds 一次性查询，避免逐课查询造成 N+1 调用。
        List<Long> courseIds = topCourses.stream()
                .map(RecommendItemDTO::getCourseId)
                .collect(Collectors.toList());
        List<CourseReadinessDTO> readinessList = courseGraphRepository.getCourseReadinessBatch(userId, courseIds,
                PREREQUISITE_THRESHOLD);

        Map<Long, CourseReadinessDTO> readinessMap = readinessList.stream()
                .collect(Collectors.toMap(CourseReadinessDTO::getCourseId, r -> r, (a, b) -> a));

        // 5) 批量查询课程标题，避免在流中逐条查库。
        Map<Long, String> courseTitleMap = courseService.getCourseTitleMapByIds(courseIds);

        // 6) 构建融合结果并按 finalScore 排序。
        // 当前默认不启用 readiness 过滤，保留策略开关位置以便后续 AB 或运营调参。
        List<HybridRecommendItemDTO> hybridItems = topCourses.stream()
                .map(item -> buildHybridBaseItem(item, min, max, eps, courseTitleMap, readinessMap))
                // 过滤可学习性不足的课程
                // .filter(dto -> dto.getReadiness() == null || dto.getReadiness() >=
                // READINESS_THRESHOLD)
                .sorted(Comparator.comparing(HybridRecommendItemDTO::getFinalScore).reversed())
                .collect(Collectors.toList());

        // 7) 统一补全图谱解释字段（知识点、缺失先修、学习路径）。
        enrichGraphInfo(userId, hybridItems, readinessMap);

        // 8) 组装响应并写缓存。
        HybridRecommendResponseDTO result = new HybridRecommendResponseDTO(userId, hybridItems);
        redisTemplate.opsForValue().set(cacheKey, result);
        return result;
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
        return dto;
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
     * - 非冷启动主流程可复用已算好的 readinessMap，避免重复查询。
     * - 冷启动分支传 null 时，在本方法内按 courseIds 批量补查。
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

        // 允许上游复用 readinessMap；若未传入，则在这里统一批量查询一次。
        Map<Long, CourseReadinessDTO> effectiveReadinessMap = readinessMap;
        if (effectiveReadinessMap == null) {
            List<CourseReadinessDTO> readinessList = courseGraphRepository.getCourseReadinessBatch(userId, courseIds,
                    PREREQUISITE_THRESHOLD);
            effectiveReadinessMap = readinessList.stream()
                    .collect(Collectors.toMap(CourseReadinessDTO::getCourseId, r -> r, (a, b) -> a));
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
