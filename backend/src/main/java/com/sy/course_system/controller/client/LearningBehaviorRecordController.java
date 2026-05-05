package com.sy.course_system.controller.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sy.course_system.common.Result;
import com.sy.course_system.enums.LearnBehaviorType;
import com.sy.course_system.service.LearningBehaviorService;

/**
 * 学习行为控制器
 * 用于记录用户的学习行为，包括课程浏览、完成、学习时长和收藏等
 */
@RestController
@RequestMapping("/behavior")
public class LearningBehaviorRecordController {

    @Autowired
    private LearningBehaviorService learningBehaviorService;

    /**
     * 统一行为记录接口（非 STUDY）
     */
    @PostMapping("/record")
    public Result<?> recordBehavior(
        @RequestParam Long courseId, 
        @RequestParam LearnBehaviorType behaviorType,
        @RequestParam(required = false) Integer duration
        ) {
        // 记录用户的学习行为逻辑
        learningBehaviorService.recordBehavior(courseId, behaviorType, duration);
        return Result.success(null);
    }

}
