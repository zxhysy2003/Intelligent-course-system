package com.sy.course_system.service;

import com.sy.course_system.dto.graph.KnowledgeGraphResponseDTO;

public interface KnowledgeGraphService {
    KnowledgeGraphResponseDTO getKnowledgeGraph(Long courseId, Long userId, Integer depth);
}
