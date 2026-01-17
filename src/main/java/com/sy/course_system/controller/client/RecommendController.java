package com.sy.course_system.controller.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sy.course_system.common.Result;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.service.impl.RecommendServiceImpl;
import com.sy.course_system.vo.CourseRecommendVO;

/**
 * 课程推荐控制器
 * 基于用户学习行为和偏好为用户推荐适合的课程
 */
@RestController
@RequestMapping("/recommend")
public class RecommendController {
    @Autowired
    private RecommendServiceImpl recommendService;

    /**
     * 获取当前用户的课程推荐列表
     * 根据用户的学习行为、学习进度等信息进行个性化推荐
     * @return 返回推荐课程列表，包含课程基本信息和推荐理由
     */
    @GetMapping("/courses")
    public Result<List<CourseRecommendVO>> recommendCourses() {

        Long userId = UserContext.getUserId();

        List<CourseRecommendVO> recommendList = recommendService.recommendCourseVO(userId);

        return Result.success(recommendList);
    }
}
