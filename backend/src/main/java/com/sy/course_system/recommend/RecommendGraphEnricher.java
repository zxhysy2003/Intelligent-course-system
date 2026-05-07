package com.sy.course_system.recommend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import com.sy.course_system.common.util.ConcurrentUtils;
import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.graph.CourseKnowledgePointDTO;
import com.sy.course_system.dto.recommend.CourseReadinessDTO;
import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.repository.CourseGraphRepository;
import com.sy.course_system.vo.KnowledgeMasteryVO;
import com.sy.course_system.vo.KnowledgeVO;

/**
 * 图谱补全组件：为推荐结果统一填充可解释字段。
 *
 * 补全内容：
 * 1) readiness：可学习性（如果基础项尚未写入）；
 * 2) missingPrerequisitesMastery：缺失先修及掌握度；
 * 3) knowledgePoints：课程知识点概览；
 * 4) learningPaths：建议补齐路径（按先修关系展开）。
 *
 * 性能注意：
 * - 非冷启动主流程可复用已算好的 readinessMap；若仅覆盖部分课程，会自动补查缺口。
 * - 冷启动分支传 null 时，在本方法内按 courseIds 批量查询。
 */
@Component
public class RecommendGraphEnricher {

    private static final String GRAPH_ASYNC_INTERRUPTED_MSG = "图谱补全异步执行被中断";

    private final CourseGraphRepository courseGraphRepository;
    private final Neo4jClient neo4jClient;
    private final Executor recommendTaskExecutor;
    private final RecommendProperties recommendProperties;

    public RecommendGraphEnricher(CourseGraphRepository courseGraphRepository,
            Neo4jClient neo4jClient,
            @Qualifier("recommendTaskExecutor") Executor recommendTaskExecutor,
            RecommendProperties recommendProperties) {
        this.courseGraphRepository = courseGraphRepository;
        this.neo4jClient = neo4jClient;
        this.recommendTaskExecutor = recommendTaskExecutor;
        this.recommendProperties = recommendProperties;
    }

    public void enrichGraphInfo(Long userId, List<HybridRecommendItemDTO> items,
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

        Map<Long, CourseReadinessDTO> effectiveReadinessMap = readinessMap == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(readinessMap);
        List<Long> missingReadinessCourseIds = courseIds.stream()
                .filter(id -> !effectiveReadinessMap.containsKey(id))
                .toList();

        Map<Long, List<KnowledgeVO>> knowledgePointsMap;
        Map<Long, List<List<KnowledgeVO>>> learningPathsMap;
        double prerequisiteThreshold = recommendProperties.getGraph().getPrerequisiteThreshold();
        int learningPathLimit = recommendProperties.getGraph().getLearningPathLimitPerCourse();

        if (recommendProperties.getAsync().isEnabled()) {
            CompletableFuture<Map<Long, CourseReadinessDTO>> readinessFuture;
            if (!missingReadinessCourseIds.isEmpty()) {
                readinessFuture = CompletableFuture.supplyAsync(() -> {
                    List<CourseReadinessDTO> readinessList = courseGraphRepository.getCourseReadinessBatch(
                            userId, missingReadinessCourseIds, prerequisiteThreshold);
                    return toReadinessMap(readinessList);
                }, recommendTaskExecutor);
            } else {
                readinessFuture = CompletableFuture.completedFuture(Map.of());
            }

            CompletableFuture<Map<Long, List<KnowledgeVO>>> kpFuture = CompletableFuture.supplyAsync(
                    () -> loadCourseKnowledgePointsMap(courseIds), recommendTaskExecutor);

            CompletableFuture<Map<Long, List<List<KnowledgeVO>>>> pathFuture = CompletableFuture.supplyAsync(
                    () -> loadLearningPathsMap(userId, courseIds, prerequisiteThreshold, learningPathLimit),
                    recommendTaskExecutor);

            ConcurrentUtils.awaitAll(GRAPH_ASYNC_INTERRUPTED_MSG, readinessFuture, kpFuture, pathFuture);
            Map<Long, CourseReadinessDTO> fetchedReadinessMap = readinessFuture.getNow(Map.of());
            if (!fetchedReadinessMap.isEmpty()) {
                effectiveReadinessMap.putAll(fetchedReadinessMap);
            }
            knowledgePointsMap = kpFuture.getNow(Map.of());
            learningPathsMap = pathFuture.getNow(Map.of());
        } else {
            if (!missingReadinessCourseIds.isEmpty()) {
                List<CourseReadinessDTO> readinessList = courseGraphRepository.getCourseReadinessBatch(
                        userId, missingReadinessCourseIds, prerequisiteThreshold);
                Map<Long, CourseReadinessDTO> fetchedReadinessMap = toReadinessMap(readinessList);
                if (!fetchedReadinessMap.isEmpty()) {
                    effectiveReadinessMap.putAll(fetchedReadinessMap);
                }
            }
            knowledgePointsMap = loadCourseKnowledgePointsMap(courseIds);
            learningPathsMap = loadLearningPathsMap(userId, courseIds, prerequisiteThreshold, learningPathLimit);
        }

        for (HybridRecommendItemDTO item : items) {
            Long courseId = item.getCourseId();
            if (courseId == null) {
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

            List<KnowledgeMasteryVO> missing = (readinessDTO == null || readinessDTO.getMissing() == null) ? List.of()
                    : readinessDTO.getMissing();
            item.setMissingPrerequisitesMastery(missing);

            item.setKnowledgePoints(knowledgePointsMap.getOrDefault(courseId, List.of()));
            item.setLearningPaths(learningPathsMap.getOrDefault(courseId, List.of()));
        }
    }

    public Map<Long, CourseReadinessDTO> toReadinessMap(List<CourseReadinessDTO> readinessList) {
        Map<Long, CourseReadinessDTO> map = new LinkedHashMap<>();
        if (readinessList == null || readinessList.isEmpty()) {
            return map;
        }
        for (CourseReadinessDTO readiness : readinessList) {
            if (readiness == null || readiness.getCourseId() == null) {
                continue;
            }
            map.putIfAbsent(readiness.getCourseId(), readiness);
        }
        return map;
    }

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
