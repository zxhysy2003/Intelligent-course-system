package com.sy.course_system.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.HybridRecommendService;
import com.sy.course_system.service.RecommendService;

@Service
public class HybridRecommendServiceImpl implements HybridRecommendService {

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

    // 可以缓存用户推荐结果，提升性能
    private static final String RECOMMEND_COURSE_KEY = "recommend:user:";

    // 候选池大小（避免Neo4j压力）
    private static final int CANDIDATE_POOL_SIZE = 20;

    // 先修掌握阈值：低于此认为缺失
    private static final double PREREQUISITE_THRESHOLD = 0.7;

    // 融合权重
    private static final double CF_WEIGHT = 0.7;

    // 可学习性门槛
    private static final double READINESS_THRESHOLD = 0.4;

    /**
     * 融合推荐入口
     * CF推荐课程 + 图谱补齐路径
     */
    @Override
    public HybridRecommendResponseDTO recommend(Long userId) {
        String cacheKey = RECOMMEND_COURSE_KEY + userId;

        // 0. 先查缓存
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (cached instanceof HybridRecommendResponseDTO dto) {
                return dto;
            }
            // 兼容反序列化为 Map 的情况
            HybridRecommendResponseDTO dto = objectMapper.convertValue(cached, HybridRecommendResponseDTO.class);
            if (dto != null) {
                return dto;
            }
        }

        // 1.调用CF推荐服务，获取推荐课程列表
        RecommendResponseDTO cfResp = recommendService.recommend(userId);

        List<RecommendItemDTO> items = cfResp.getItems();
        if (items == null || items.isEmpty()) {
            HybridRecommendResponseDTO empty = new HybridRecommendResponseDTO(userId, List.of());
            redisTemplate.opsForValue().set(cacheKey, empty);
            return empty;
        }

        // 2.取 topN 课程作为候选池
        List<RecommendItemDTO> topCourses = items.stream()
                .sorted(Comparator.comparing(RecommendItemDTO::getScore).reversed())
                .limit(CANDIDATE_POOL_SIZE)
                .collect(Collectors.toList());

        // 3.CF分数归一化参数
        double min = topCourses.stream()
                .mapToDouble(RecommendItemDTO::getScore)
                .min()
                .orElse(0.0);
        double max = topCourses.stream()
                .mapToDouble(RecommendItemDTO::getScore)
                .max()
                .orElse(1.0);
        double eps = 1e-9;

        // 4.Neo4j批量计算 readiness + missing (含 have/need)
        List<Long> courseIds = topCourses.stream()
                .map(RecommendItemDTO::getCourseId)
                .collect(Collectors.toList());
        List<CourseReadinessDTO> readinessList = courseGraphRepository.getCourseReadinessBatch(userId, courseIds,
                PREREQUISITE_THRESHOLD);

        Map<Long, CourseReadinessDTO> readinessMap = readinessList.stream()
                .collect(Collectors.toMap(CourseReadinessDTO::getCourseId, r -> r));

        // 5.根据courseIds得到对应的课程名
        Map<Long, String> courseTitleMap = courseService.getCourseTitleMapByIds(courseIds);

        // 6. 构建融合结果
        List<HybridRecommendItemDTO> hybridItems = topCourses.stream()
                .map(item -> buildHybridItem(userId, item, min, max, eps, courseTitleMap,readinessMap))
                // 过滤可学习性不足的课程
                // .filter(dto -> dto.getReadiness() == null || dto.getReadiness() >=
                // READINESS_THRESHOLD)
                .sorted(Comparator.comparing(HybridRecommendItemDTO::getFinalScore).reversed())
                .collect(Collectors.toList());

        // 7.返回结果
        HybridRecommendResponseDTO result = new HybridRecommendResponseDTO(userId, hybridItems);
        // 8.缓存结果
        redisTemplate.opsForValue().set(cacheKey, result);
        return result;
    }

    /**
     * 构建融合推荐项
     */
    private HybridRecommendItemDTO buildHybridItem(Long userId,
            RecommendItemDTO item, double min, double max, double eps, Map<Long, String> courseTitleMap, Map<Long, CourseReadinessDTO> readinessMap) {
        Long courseId = item.getCourseId();
        String title = courseTitleMap.get(courseId);
        Double cfScore = item.getScore();

        // 1.readiness + missing (带 mastery)
        CourseReadinessDTO readinessDTO = readinessMap.get(courseId);
        double readiness = (readinessDTO == null || readinessDTO.getReadiness() == null) ? 1.0
                : readinessDTO.getReadiness();
        List<KnowledgeMasteryDTO> missing = (readinessDTO == null || readinessDTO.getMissing() == null) ? List.of()
                : readinessDTO.getMissing();

        // 2.课程知识点
        List<KnowledgeNode> kps = courseGraphRepository.findCourseKnowledgePoints(courseId);
        List<KnowledgeBriefDTO> kpDTOs = convertBrief(kps);

        // 3. 学习路径
        List<List<KnowledgeNode>> paths = findLearningPathsRawViaClient(userId, courseId,
                PREREQUISITE_THRESHOLD, 5);
        List<List<KnowledgeBriefDTO>> pathDTOs = paths.stream()
                .map(this::convertBrief)
                .collect(Collectors.toList());

        // 4.CF归一化 + 融合
        double cfNorm = (cfScore - min) / (max - min + eps);
        double finalScore = CF_WEIGHT * cfNorm + (1 - CF_WEIGHT) * readiness;

        // 5.构建DTO
        HybridRecommendItemDTO dto = new HybridRecommendItemDTO(courseId, title, cfScore, readiness, finalScore, kpDTOs,
                missing, pathDTOs);

        return dto;
    }

    /**
     * 知识点节点转DTO KnowledgeNode -> KnowledgeBriefDTO
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
     * 通过Neo4jClient原生查询获取学习路径（包含掌握度过滤）
     */
    private List<List<KnowledgeNode>> findLearningPathsRawViaClient(Long userId, Long courseId, double threshold,
            int limit) {

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
                    // 这里 SDN Neo4jClient fetch 出来的一般就是 Java List<Map>
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
