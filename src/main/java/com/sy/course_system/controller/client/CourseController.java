package com.sy.course_system.controller.client;

import com.sy.course_system.common.PageResult;
import com.sy.course_system.common.Result;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.dto.course.CourseQueryDTO;
import com.sy.course_system.entity.UserCourseRelation;
import com.sy.course_system.mapper.CategoryMapper;
import com.sy.course_system.vo.CategoryVO;
import com.sy.course_system.vo.CourseDetailVO;
import com.sy.course_system.vo.CourseVO;
import com.sy.course_system.vo.KnowledgeVO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.UserCourseService;

import java.util.List;

@RestController
@RequestMapping("/course")
public class CourseController {
    @Autowired
    private CourseService courseService;

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private UserCourseService userCourseService;

    // 根据课程id获取课程详情
    @GetMapping("/{courseId}")
    public Result<CourseDetailVO> getCourseById(@PathVariable Long courseId) {
        CourseDetailVO courseDetailVO = courseService.getCourseByIdForUser(courseId);
        if (courseDetailVO == null) {
            return Result.error(404, "课程未找到。");
        }
        return Result.success(courseDetailVO);
    }

    // 根据知识点id获取课程详情集合
    @GetMapping("/by-kp")
    public Result<List<CourseDetailVO>> getCourseDetailsByKnowledgePointId(@RequestParam Long kpId) {
        List<CourseDetailVO> courseDetails = courseService.getCourseDetailsByKnowledgePointId(kpId);
        if (courseDetails == null || courseDetails.isEmpty()) {
            return Result.error(404, "未找到对应课程。");
        }
        return Result.success(courseDetails);
    }
    // 根据课程id获取课程关联的知识点列表
    @GetMapping("/by-c")
    public Result<List<KnowledgeVO>> getKnowledgePointsByCourseId(@RequestParam Long courseId) {
        List<KnowledgeVO> knowledgePoints = courseService.getKnowledgePointsByCourseId(courseId);
        if (knowledgePoints == null || knowledgePoints.isEmpty()) {
            return Result.error(404, "未找到对应知识点。");
        }
        return Result.success(knowledgePoints);
    }

    // 课程列表（分页+条件）
    @PostMapping("/list")
    public Result<PageResult<CourseVO>> list(@RequestBody CourseQueryDTO queryDTO) {
        return Result.success(courseService.pageForUser(queryDTO));
    }

    // 课程分类列表
    @GetMapping("/categories")
    public Result<List<CategoryVO>> listCategories() {
        return Result.success(categoryMapper.selectList(null).stream().map(entity -> {
            CategoryVO vo = new CategoryVO();
            vo.setId(entity.getId());
            vo.setName(entity.getName());
            return vo;
        }).toList());
    }

    // 用户注册课程
    @GetMapping("/attend/{courseId}")
    public Result<Boolean> userAttendCourse(@PathVariable Long courseId) {
        Boolean status = userCourseService.userAttendCourse(courseId);
        if (!status) {
            return Result.error(400,"用户已添加过该课程。");
        }
        return Result.success("添加课程成功。", status);
    }

    // 获取课程视频地址
    @GetMapping("/video/{courseId}")
    public Result<String> getCourseVideo(@PathVariable Long courseId) {
        String videoPath = courseService.getCourseVideoPath(courseId);
        if (videoPath == null || videoPath.isEmpty()) {
            return Result.error(404, "课程视频未找到。");
        }
        String normalized = videoPath;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String videoUrl;
        if (normalized.contains(".")) {
            videoUrl = "/videos/" + normalized;
        } else {
            videoUrl = "/videos/" + normalized + ".mp4";
        }
        return Result.success(videoUrl);
    }

    // 获取用户与课程的关系状态
    @GetMapping("/relation/{courseId}")
    public Result<UserCourseRelation> getUserCourseRelation(@PathVariable Long courseId) {
        UserCourseRelation relation = userCourseService.getUserCourseRelation(UserContext.getUserId(), courseId);
        return Result.success(relation);
    }

    // 更新课程视频断点信息，以便下次继续学习
    @PostMapping("/relation/updateProgressSeconds")
    public Result<Integer> updateCourseVideoProgressSeconds(@RequestParam Long courseId, @RequestParam Integer progressSeconds) {
        UserCourseRelation relation = new UserCourseRelation();
        relation.setUserId(UserContext.getUserId());
        relation.setCourseId(courseId);
        relation.setProgressSeconds(progressSeconds);
        int updatedRecords = userCourseService.updateUserCourseRelation(relation);
        if (updatedRecords == 0) {
            return Result.error(400, "更新课程进度失败，关联关系不存在。");
        }
        return Result.success(updatedRecords);
    }

}
