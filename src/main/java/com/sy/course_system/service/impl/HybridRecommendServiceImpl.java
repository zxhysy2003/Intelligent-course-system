package com.sy.course_system.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sy.course_system.dto.recommend.CourseReadinessDTO;
import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;
import com.sy.course_system.dto.recommend.KnowledgeBriefDTO;
import com.sy.course_system.dto.recommend.KnowledgeMasteryDTO;
import com.sy.course_system.dto.recommend.RecommendItemDTO;
import com.sy.course_system.dto.recommend.RecommendResponseDTO;
import com.sy.course_system.graph.node.KnowledgeNode;
import com.sy.course_system.repository.CourseGraphRepository;
import com.sy.course_system.service.HybridRecommendService;
import com.sy.course_system.service.RecommendService;

@Service
public class HybridRecommendServiceImpl implements HybridRecommendService {

    @Autowired
    private RecommendService recommendService;
    @Autowired
    private CourseGraphRepository courseGraphRepository;
    // TODO: 可以缓存用户推荐结果，提升性能
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
        // 1.调用CF推荐服务，获取推荐课程列表
        RecommendResponseDTO cfResp = recommendService.recommend(userId);

        List<RecommendItemDTO> items = cfResp.getItems();
        if (items == null || items.isEmpty()) {
            return new HybridRecommendResponseDTO(userId, List.of());
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
        List<CourseReadinessDTO> readinessList = courseGraphRepository.getCourseReadinessBatch(userId, courseIds, PREREQUISITE_THRESHOLD);

        Map<Long, CourseReadinessDTO> readinessMap = readinessList.stream()
                                                                    .collect(Collectors.toMap(CourseReadinessDTO::getCourseId, r -> r));
        
        // 5. 构建融合结果
        List<HybridRecommendItemDTO> hybridItems = topCourses.stream()
        .map(item -> buildHybridItem(userId, item, min, max, eps, readinessMap))
        // 过滤可学习性不足的课程
        .filter(dto -> dto.getReadiness() == null || dto.getReadiness() >= READINESS_THRESHOLD)
        .sorted(Comparator.comparing(HybridRecommendItemDTO::getFinalScore).reversed())
        .collect(Collectors.toList());

        // 6.返回结果
        return new HybridRecommendResponseDTO(userId, hybridItems);
    }

    /**
     * 构建融合推荐项
     */
    private HybridRecommendItemDTO buildHybridItem(Long userId, 
        RecommendItemDTO item, double min, double max, double eps, Map<Long, CourseReadinessDTO> readinessMap) {
        Long courseId = item.getCourseId();
        Double cfScore = item.getScore();

        // 1.readiness + missing (带 mastery)
        CourseReadinessDTO readinessDTO = readinessMap.get(courseId);
        double readiness = (readinessDTO == null || readinessDTO.getReadiness() == null) ? 1.0 : readinessDTO.getReadiness();
        List<KnowledgeMasteryDTO> missing = (readinessDTO == null || readinessDTO.getMissing() == null) ? List.of() : readinessDTO.getMissing();
        
        // 2.课程知识点
        List<KnowledgeNode> kps = courseGraphRepository.findCourseKnowledgePoints(courseId);
        List<KnowledgeBriefDTO> kpDTOs = convertBrief(kps);

        // 3. 学习路径
        List<List<KnowledgeNode>> paths = courseGraphRepository.findLearningPaths(userId, courseId, PREREQUISITE_THRESHOLD, 5);
        List<List<KnowledgeBriefDTO>> pathDTOs = paths.stream()
                                                    .map(this::convertBrief)
                                                    .collect(Collectors.toList());
        
        // 4.CF归一化 + 融合
        double cfNorm = (cfScore - min) / (max - min + eps);
        double finalScore = CF_WEIGHT * cfNorm + (1 - CF_WEIGHT) * readiness;

        // 5.构建DTO
        HybridRecommendItemDTO dto = new HybridRecommendItemDTO(courseId, cfScore, readiness, finalScore, kpDTOs, missing, pathDTOs);

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
                        k.getDifficulty()
                    ))
                    .collect(Collectors.toList());
    }

    
}
