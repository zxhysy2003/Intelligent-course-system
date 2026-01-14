package com.sy.course_system.service.impl;

import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sy.course_system.common.UserContext;
import com.sy.course_system.dto.LearningPathDTO;
import com.sy.course_system.graph.node.KnowledgePointNode;
import com.sy.course_system.repository.KnowledgePointRepository;
import com.sy.course_system.repository.LearningPathRepository;
import com.sy.course_system.service.LearningPathService;
import com.sy.course_system.vo.LearningPathRecommendVO;

@Service
public class LearningPathServiceImpl implements LearningPathService {

    @Autowired
    private LearningPathRepository learningPathRepository;

    @Autowired
    private KnowledgePointRepository knowledgePointRepository;

    @Override
    public List<LearningPathRecommendVO> recommendPath(Long courseId) {
        
        List<LearningPathDTO> nodes = learningPathRepository.recommendPath(courseId, 5);

        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }

        return nodes.stream().map(dto -> {
            LearningPathRecommendVO vo = new LearningPathRecommendVO();
            vo.setFromId(dto.getFromId());
            vo.setFromName(dto.getFromName());
            vo.setToId(dto.getToId());
            vo.setToName(dto.getToName());
            vo.setSupport(dto.getSupport());
            vo.buildReason();
            return vo;
        }).toList();

    }

    /**
     * 为用户根据当前所学知识点推荐后续知识点的学习路径
     */
    @Override
    public List<List<KnowledgePointNode>> recommendLearningPathsForUser(Long kpId) {
        return knowledgePointRepository.recommendLearningPaths(UserContext.getUserId(), kpId);
    }

}
