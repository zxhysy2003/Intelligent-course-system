package com.sy.course_system.controller.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sy.course_system.common.Result;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.dto.AbilityRadarDTO;
import com.sy.course_system.dto.ProgressChartDTO;
import com.sy.course_system.dto.graph.KnowledgeGraphResponseDTO;
import com.sy.course_system.service.KnowledgeGraphService;
import com.sy.course_system.service.LearningAnalysisService;

@RestController
@RequestMapping("/analysis")
public class LearningAnalysisController {

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;
    @Autowired
    private LearningAnalysisService learningAnalysisService;

    // 知识图谱（课程知识点 + 先修关系 + 掌握度）
    @GetMapping("/knowledge-graph")
    public Result<KnowledgeGraphResponseDTO> knowledgeGraph(@RequestParam Long courseId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false, defaultValue = "3") Integer depth) {
        Long uid = userId != null ? userId : UserContext.getUserId();
        return Result.success(knowledgeGraphService.getKnowledgeGraph(courseId, uid, depth));
    }

    // 学习进度图数据（折线/柱状图）
    @GetMapping("/progress")
    public Result<ProgressChartDTO> progress(@RequestParam(required = false, defaultValue = "30") Integer days,
            @RequestParam(required = false) Long userId) {
        Long uid = userId != null ? userId : UserContext.getUserId();
        return Result.success(learningAnalysisService.getProgressChart(uid, days));
    }

    // 能力雷达图数据
    @GetMapping("/ability-radar")
    public Result<AbilityRadarDTO> abilityRadar(@RequestParam(required = false) Long userId) {
        Long uid = userId != null ? userId : UserContext.getUserId();
        return Result.success(learningAnalysisService.getAbilityRadar(uid));
    }

}
