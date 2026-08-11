package com.sy.course_system.controller.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sy.course_system.common.ApiPaths;
import com.sy.course_system.common.Result;
import com.sy.course_system.dto.LearningBehaviorRecordDTO;
import com.sy.course_system.enums.LearnBehaviorType;
import com.sy.course_system.service.LearningBehaviorService;

/**
 * 学习行为控制器
 * 用于记录用户的学习行为，包括课程浏览、学习时长和收藏等。
 * 完成行为由 STUDY 首次达到完成条件时自动生成，不接受外部直接提交。
 */
@RestController
@RequestMapping(ApiPaths.LEARNING_BEHAVIORS)
public class LearningBehaviorRecordController {

    @Autowired
    private LearningBehaviorService learningBehaviorService;

    /**
     * 统一行为记录接口
     */
    @PostMapping
    public Result<?> recordBehavior(@RequestBody LearningBehaviorRecordDTO request) {
        if (request == null || request.getCourseId() == null || request.getBehaviorType() == null) {
            return Result.error(400, "courseId 和 behaviorType 不能为空");
        }
        if (request.getBehaviorType() == LearnBehaviorType.FINISH) {
            return Result.error(400, "FINISH 行为由学习进度自动生成，不能直接提交");
        }
        learningBehaviorService.recordBehavior(request.getCourseId(), request.getBehaviorType(), request.getDuration());
        return Result.success(null);
    }

}
