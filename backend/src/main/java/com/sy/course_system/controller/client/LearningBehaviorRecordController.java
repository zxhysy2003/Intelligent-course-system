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
 * 用于记录用户的学习行为，包括课程浏览、学习时长和收藏等。
 * 完成行为由 STUDY 首次达到完成条件时自动生成，不接受外部直接提交。
 */
@RestController
@RequestMapping("/behavior")
public class LearningBehaviorRecordController {

    @Autowired
    private LearningBehaviorService learningBehaviorService;

    /**
     * 统一行为记录接口
     */
    @PostMapping("/record")
    public Result<?> recordBehavior(
        @RequestParam Long courseId, 
        @RequestParam LearnBehaviorType behaviorType,
        @RequestParam(required = false) Integer duration
        ) {
        if (behaviorType == LearnBehaviorType.FINISH) {
            return Result.error(400, "FINISH 行为由学习进度自动生成，不能直接提交");
        }
        // 记录用户的学习行为逻辑
        learningBehaviorService.recordBehavior(courseId, behaviorType, duration);
        return Result.success(null);
    }

}
