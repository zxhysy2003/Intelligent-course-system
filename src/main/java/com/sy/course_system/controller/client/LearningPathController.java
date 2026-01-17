package com.sy.course_system.controller.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sy.course_system.common.Result;
import com.sy.course_system.graph.node.KnowledgePointNode;
import com.sy.course_system.service.LearningPathService;

@RestController
@RequestMapping("/path")
public class LearningPathController {
    @Autowired
    private LearningPathService learningPathService;

    // 找出所有学过这门课的人，看看他们之后还学了什么课，推荐最受欢迎的几个后续课程
    @GetMapping("/graph/{courseId}")
    public Result<?> recommendPath(@PathVariable Long courseId) {
        return Result.success(learningPathService.recommendPath(courseId));
    }

    // 根据知识点推荐学习路径
    @GetMapping("/recommend")
    public Result<List<List<KnowledgePointNode>>> recommendLearningPaths(@RequestParam Long kpId) {
        return Result.success(learningPathService.recommendLearningPathsForUser(kpId));
    }
}
