package com.sy.course_system.controller.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sy.course_system.common.Result;
import com.sy.course_system.dto.course.CourseDeleteDTO;
import com.sy.course_system.dto.course.CourseRegisterDTO;
import com.sy.course_system.service.CourseService;

@RestController
@RequestMapping("admin/course")
public class CourseAdminController {
    
    @Autowired
    private CourseService courseService;

    /**
     * 课程注册
     * @param registerDTO 注册信息，包含课程的相关信息
     * @return 返回注册结果，成功返回提示信息，失败返回对应错误信息
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody CourseRegisterDTO registerDTO) {
        try {
            Integer res = courseService.register(registerDTO);
            if (res == null) {
                return Result.error(500, "添加失败");
            } else if (res == -1) {
                return Result.error(400, "课程已存在");
            }
            return Result.success("注册成功");
        } catch (RuntimeException ex) {
            return Result.error(500, "添加失败");
        }
    }

    @DeleteMapping("/delete")
    public Result<String> delete(@RequestBody CourseDeleteDTO deleteDTO) {
        boolean deleted = courseService.removeCourses(deleteDTO.getCourseIds());
        if (deleted) {
            return Result.success("删除成功");
        } else {
            return Result.error(500, "删除失败");
        }
    }

}
