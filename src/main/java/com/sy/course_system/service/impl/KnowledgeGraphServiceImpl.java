package com.sy.course_system.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sy.course_system.dto.graph.KnowledgeGraphLinkDTO;
import com.sy.course_system.dto.graph.KnowledgeGraphNodeDTO;
import com.sy.course_system.dto.graph.KnowledgeGraphResponseDTO;
import com.sy.course_system.dto.graph.KnowledgeGraphStatsDTO;
import com.sy.course_system.repository.CourseGraphRepository;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.KnowledgeGraphService;
import com.sy.course_system.vo.CourseDetailVO;

@Service
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {
    private static final String KP_PREFIX = "kp_";

    @Autowired
    private CourseGraphRepository courseGraphRepository;
    @Autowired
    private CourseService courseService;

    @Override
    public KnowledgeGraphResponseDTO getKnowledgeGraph(Long courseId, Long userId, Integer depth) {
        int safeDepth = (depth == null || depth <= 0) ? 3 : depth;
        List<KnowledgeGraphNodeDTO> nodes = courseGraphRepository.findKnowledgeGraphNodes(courseId, userId, safeDepth);
        List<KnowledgeGraphLinkDTO> links = courseGraphRepository.findKnowledgeGraphLinks(courseId, safeDepth);

        if (nodes != null) {
            for (KnowledgeGraphNodeDTO n : nodes) {
                if (n.getKpId() != null) {
                    n.setId(KP_PREFIX + n.getKpId());
                }
                if (n.getMastery() == null) {
                    n.setMastery(0.0);
                }
                if (n.getInCourse() == null) {
                    n.setInCourse(Boolean.FALSE);
                }
                if (n.getDepth() == null) {
                    n.setDepth(0);
                }
            }
        }

        if (links != null) {
            for (KnowledgeGraphLinkDTO l : links) {
                if (l.getSourceId() != null) {
                    l.setSource(KP_PREFIX + l.getSourceId());
                }
                if (l.getTargetId() != null) {
                    l.setTarget(KP_PREFIX + l.getTargetId());
                }
                if (l.getType() == null) {
                    l.setType("PRE_REQUIRES");
                }
            }
        }

        int nodeCount = nodes == null ? 0 : nodes.size();
        int edgeCount = links == null ? 0 : links.size();
        double avgMastery = 0.0;
        if (nodes != null && !nodes.isEmpty()) {
            double sum = 0.0;
            int cnt = 0;
            for (KnowledgeGraphNodeDTO n : nodes) {
                if (n.getMastery() != null) {
                    sum += n.getMastery();
                    cnt++;
                }
            }
            avgMastery = cnt == 0 ? 0.0 : sum / cnt;
        }

        KnowledgeGraphStatsDTO stats = new KnowledgeGraphStatsDTO(nodeCount, edgeCount, avgMastery);
        CourseDetailVO courseDetail = courseService.getCourseByIdForUser(courseId);
        String title = courseDetail != null ? courseDetail.getTitle() : null;
        return new KnowledgeGraphResponseDTO(courseId, title, userId, nodes, links, stats);
    }
}
