package com.sy.course_system.controller.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sy.course_system.common.Result;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.dto.graph.KnowledgeGraphResponseDTO;
import com.sy.course_system.service.KnowledgeGraphService;

@RestController
@RequestMapping("/analysis")
public class KnowledgeGraphController {

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;

    // 知识图谱（课程知识点 + 先修关系 + 掌握度）
    @GetMapping("/knowledge-graph")
    public Result<KnowledgeGraphResponseDTO> knowledgeGraph(@RequestParam Long courseId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false, defaultValue = "3") Integer depth) {
        Long uid = userId != null ? userId : UserContext.getUserId();
        return Result.success(knowledgeGraphService.getKnowledgeGraph(courseId, uid, depth));
    }

    
}
