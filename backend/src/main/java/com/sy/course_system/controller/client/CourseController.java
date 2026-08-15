package com.sy.course_system.controller.client;

import com.sy.course_system.common.ApiPaths;
import com.sy.course_system.common.PageResult;
import com.sy.course_system.common.Result;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.dto.EnrollmentProgressUpdateDTO;
import com.sy.course_system.dto.course.CourseQueryDTO;
import com.sy.course_system.entity.UserCourseRelation;
import com.sy.course_system.enums.CourseStatus;
import com.sy.course_system.mapper.CategoryMapper;
import com.sy.course_system.vo.CategoryVO;
import com.sy.course_system.vo.CoursePlaybackVO;
import com.sy.course_system.vo.CourseDetailVO;
import com.sy.course_system.vo.CourseVO;
import com.sy.course_system.vo.KnowledgeVO;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.UserCourseService;
import com.sy.course_system.service.VideoPlaybackService;

import java.util.List;

@RestController
@RequestMapping(ApiPaths.API_V1)
public class CourseController {
    private final CourseService courseService;

    private final CategoryMapper categoryMapper;

    private final UserCourseService userCourseService;

    private final VideoPlaybackService videoPlaybackService;

    public CourseController(CourseService courseService, CategoryMapper categoryMapper,
            UserCourseService userCourseService,
            VideoPlaybackService videoPlaybackService) {
        this.courseService = courseService;
        this.categoryMapper = categoryMapper;
        this.userCourseService = userCourseService;
        this.videoPlaybackService = videoPlaybackService;
    }

    // 根据课程id获取课程详情
    @GetMapping("/courses/{courseId}")
    public Result<CourseDetailVO> getCourseById(@PathVariable Long courseId) {
        CourseDetailVO courseDetailVO = courseService.getCourseByIdForUser(courseId);
        if (courseDetailVO == null) {
            return Result.error(404, "课程未找到。");
        }
        return Result.success(courseDetailVO);
    }

    // 根据知识点id获取课程详情集合
    @GetMapping("/knowledge-points/{knowledgePointId}/courses")
    public Result<List<CourseDetailVO>> getCourseDetailsByKnowledgePointId(@PathVariable Long knowledgePointId) {
        List<CourseDetailVO> courseDetails = courseService.getCourseDetailsByKnowledgePointId(knowledgePointId);
        if (courseDetails == null || courseDetails.isEmpty()) {
            return Result.error(404, "未找到对应课程。");
        }
        return Result.success(courseDetails);
    }

    // 根据课程id获取课程关联的知识点列表
    @GetMapping("/courses/{courseId}/knowledge-points")
    public Result<List<KnowledgeVO>> getKnowledgePointsByCourseId(@PathVariable Long courseId) {
        List<KnowledgeVO> knowledgePoints = courseService.getKnowledgePointsByCourseId(courseId);
        if (knowledgePoints == null || knowledgePoints.isEmpty()) {
            return Result.error(404, "未找到对应知识点。");
        }
        return Result.success(knowledgePoints);
    }

    // 课程列表（分页+条件）
    @PostMapping("/courses/search")
    public Result<PageResult<CourseVO>> list(@RequestBody CourseQueryDTO queryDTO) {
        queryDTO.setStatus(CourseStatus.ONLINE.getCode());
        return Result.success(courseService.pageForUser(queryDTO));
    }

    // 课程分类列表
    @GetMapping("/courses/categories")
    public Result<List<CategoryVO>> listCategories() {
        return Result.success(categoryMapper.selectList(null).stream().map(entity -> {
            CategoryVO vo = new CategoryVO();
            vo.setId(entity.getId());
            vo.setName(entity.getName());
            return vo;
        }).toList());
    }

    // 用户注册课程
    @PostMapping("/courses/{courseId}/enrollment")
    public Result<Boolean> userAttendCourse(@PathVariable Long courseId) {
        Boolean status = userCourseService.userAttendCourse(courseId);
        if (!status) {
            return Result.error(400, "用户已添加过该课程。");
        }
        return Result.success("添加课程成功。", status);
    }

    // 签发仅允许访问当前课程视频的短期播放地址
    @PostMapping("/courses/{courseId}/playback")
    public Result<CoursePlaybackVO> createCoursePlayback(@PathVariable Long courseId,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        CoursePlaybackVO playback = videoPlaybackService.issue(UserContext.getUserId(), courseId);
        if (playback == null) {
            return Result.error(404, "课程视频未找到。");
        }
        return Result.success(playback);
    }

    // 获取用户与课程的关系状态
    @GetMapping("/courses/{courseId}/enrollment")
    public Result<UserCourseRelation> getUserCourseRelation(@PathVariable Long courseId) {
        UserCourseRelation relation = userCourseService.getUserCourseRelation(UserContext.getUserId(), courseId);
        return Result.success(relation);
    }

    // 更新课程视频断点信息，以便下次继续学习
    @PatchMapping("/courses/{courseId}/enrollment/progress")
    public Result<Integer> updateCourseVideoProgressSeconds(@PathVariable Long courseId,
            @RequestBody EnrollmentProgressUpdateDTO request) {
        if (request == null || request.getProgressSeconds() == null) {
            return Result.error(400, "progressSeconds 不能为空。");
        }
        UserCourseRelation relation = new UserCourseRelation();
        relation.setUserId(UserContext.getUserId());
        relation.setCourseId(courseId);
        relation.setProgressSeconds(request.getProgressSeconds());
        int updatedRecords = userCourseService.updateUserCourseRelation(relation);
        if (updatedRecords == 0) {
            return Result.error(400, "更新课程进度失败，关联关系不存在。");
        }
        return Result.success(updatedRecords);
    }

}
