package com.sy.course_system.controller.client;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sy.course_system.common.Result;
import com.sy.course_system.service.LearningAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;


@RestController
@RequestMapping("/analysis")
public class LearningAnalysisController {
    @Autowired
    private LearningAnalysisService learningAnalysisService;
    
    // 我的学习统计
    @GetMapping("/me")
    public Result<Map<String, Object>> myAnalysis() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalStudyTime", learningAnalysisService.getMyTotalStudyTime());
        result.put("learnedCourses", learningAnalysisService.getMyLearnedCourses());
        return Result.success(result);
    }

    // 热门课程
    @GetMapping("/hot")
    public Result<?> hotCourses(@RequestParam(defaultValue = "10") Integer topN) {
        return Result.success(learningAnalysisService.getHotCourses(topN));
    }
    
}
