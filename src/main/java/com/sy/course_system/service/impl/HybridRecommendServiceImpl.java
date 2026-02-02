package com.sy.course_system.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;
import com.sy.course_system.dto.recommend.KnowledgeBriefDTO;
import com.sy.course_system.dto.recommend.RecommendItemDTO;
import com.sy.course_system.dto.recommend.RecommendResponseDTO;
import com.sy.course_system.graph.node.KnowledgeNode;
import com.sy.course_system.repository.CourseGraphRepository;
import com.sy.course_system.service.HybridRecommendService;
import com.sy.course_system.service.RecommendService;

public class HybridRecommendServiceImpl implements HybridRecommendService {

    @Autowired
    private RecommendService recommendService;
    @Autowired
    private CourseGraphRepository courseGraphRepository;
    // TODO: 可以缓存用户推荐结果，提升性能
    private static final String RECOMMEND_COURSE_KEY = "recommend:user:";
    /**
     * 融合推荐入口
     * CF推荐课程 + 图谱补齐路径
     */
    @Override
    public HybridRecommendResponseDTO recommend(Long userId) {
        // 1.调用CF推荐服务，获取推荐课程列表
        RecommendResponseDTO cfResp = recommendService.recommend(userId);

        // 2.只取钱20个候选(避免Neo4j压力)
        List<RecommendItemDTO> topCourses = cfResp.getItems()
                                                .stream()
                                                .sorted(Comparator.comparing(RecommendItemDTO::getScore).reversed())
                                                .limit(20)
                                                .collect(Collectors.toList());

        // 3.找最大CF分数用于归一化
        double maxScore = topCourses.stream()
                                    .mapToDouble(RecommendItemDTO::getScore)
                                    .max()
                                    .orElse(1.0);

        // 4.图谱补齐路径
        List<HybridRecommendItemDTO> hybridItems = topCourses.stream()
                .map(item -> buildHybridItem(userId,item, maxScore))
                .sorted(Comparator.comparing(HybridRecommendItemDTO::getFinalScore).reversed())
                .collect(Collectors.toList());

        // 5.返回结果
        return new HybridRecommendResponseDTO(userId, hybridItems);
    }

    /**
     * 构建融合推荐项
     */
    private HybridRecommendItemDTO buildHybridItem(Long userId, RecommendItemDTO item, double maxScore) {
        Long courseId = item.getCourseId();
        Double cfScore = item.getScore();

        // 图谱查询
        // 1.课程知识点
        List<KnowledgeNode> kps = courseGraphRepository.findCourseKnowledgePoints(courseId);
        // 2.用户缺失的前置知识点（最多10个）
        List<KnowledgeNode> missing = courseGraphRepository.findMissingPrerequisites(userId, courseId, 10);
        // 3.学习路径（最多5条）
        List<List<KnowledgeNode>> paths = courseGraphRepository.findLearningPaths(userId, courseId, 5);

        // 转DTO
        List<KnowledgeBriefDTO> kpDTOs = convert(kps);
        List<KnowledgeBriefDTO> missingDTOs = convert(missing);
        List<List<KnowledgeBriefDTO>> pathDTOs = paths.stream()
                                                        .map(this::convert)
                                                        .collect(Collectors.toList());
        
        // 融合排序公式
        double cfNorm = cfScore / maxScore; // 归一化CF分数
        double penalty = missing.size() * 0.1; // 缺失知识点惩罚
        double finalScore = 0.7 * cfNorm + 0.3 * (1 - penalty); // 融合得分

        // 构建DTO返回
        HybridRecommendItemDTO dto = new HybridRecommendItemDTO();
        dto.setCourseId(courseId);
        dto.setCfScore(cfScore);
        dto.setFinalScore(finalScore);
        dto.setKnowledgePoints(kpDTOs);
        dto.setMissingPrerequisites(missingDTOs);
        dto.setLearningPaths(pathDTOs);

        return dto;
    }

    /**
     * 知识点节点转DTO KnowledgeNode -> KnowledgeBriefDTO
     */
    private List<KnowledgeBriefDTO> convert(List<KnowledgeNode> nodes) {
        return nodes.stream()
                    .map(k -> new KnowledgeBriefDTO(
                        k.getId(), 
                        k.getName(),
                        k.getDifficulty()
                    ))
                    .collect(Collectors.toList());
    }

    
}
