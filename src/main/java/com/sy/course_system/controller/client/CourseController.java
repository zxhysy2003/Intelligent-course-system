package com.sy.course_system.controller.client;

import com.sy.course_system.common.PageResult;
import com.sy.course_system.common.Result;
import com.sy.course_system.dto.CourseQueryDTO;
import com.sy.course_system.mapper.CategoryMapper;
import com.sy.course_system.vo.CategoryVO;
import com.sy.course_system.vo.CourseVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sy.course_system.service.CourseService;

import java.util.List;

@RestController
@RequestMapping("/course")
public class CourseController {
    @Autowired
    private CourseService courseService;

    @Autowired
    private CategoryMapper categoryMapper;

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

    // 用户添加课程
    @GetMapping("/attend/{courseId}")
    public Result<Boolean> UserAttendCourse(@PathVariable Long courseId) {
        Boolean status = courseService.userAttendCourse(courseId);
        if (!status) {
            return Result.error(400,"用户已添加过该课程。");
        }
        return Result.success("添加课程成功。", status);
    }

    // 获取课程视频地址
    @GetMapping("/video/{courseId}")
    public Result<String> getCourseVideo(@PathVariable Long courseId) {
        // 拼接完整视频URL
        String videoPath = courseService.getCourseVideoPath(courseId);
        if (videoPath == null || videoPath.isEmpty()) {
            return Result.error(404, "课程视频未找到。");
        }
        String videoUrl = "http://localhost:8080/" + videoPath + ".mp4";
        return Result.success(videoUrl);
    }

}
