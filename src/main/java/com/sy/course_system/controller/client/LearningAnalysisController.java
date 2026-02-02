package com.sy.course_system.controller.client;

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

    // 热门课程
    @GetMapping("/hot")
    public Result<?> hotCourses(@RequestParam(defaultValue = "10") Integer topN) {
        return Result.success(learningAnalysisService.getHotCourses(topN));
    }
    
}
