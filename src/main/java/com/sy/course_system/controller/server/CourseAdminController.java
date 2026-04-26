package com.sy.course_system.controller.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.sy.course_system.common.Result;
import com.sy.course_system.dto.course.CourseDeleteDTO;
import com.sy.course_system.dto.course.CourseRegisterOptionsDTO;
import com.sy.course_system.dto.course.CourseRegisterDTO;
import com.sy.course_system.dto.course.CourseUpdateDTO;
import com.sy.course_system.enums.CourseStatus;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.VideoService;
import com.sy.course_system.vo.CourseDetailVO;
import com.sy.course_system.vo.CourseVideoUploadVO;
import com.sy.course_system.vo.CourseUpdateVO;

@RestController
@RequestMapping("admin/course")
public class CourseAdminController {
    
    @Autowired
    private CourseService courseService;
    @Autowired
    private VideoService videoService;

    @Value("${app.video.base-url}")
    private String videoBaseUrl;

    /**
     * 课程注册下拉选项
     * 返回标签和知识点（含维度）供前端选择
     */
    @GetMapping("/register-options")
    public Result<CourseRegisterOptionsDTO> registerOptions() {
        return Result.success(courseService.getRegisterOptions());
    }

    /**
     * 课程注册
     * @param registerDTO 注册信息，包含课程的相关信息
     * @return 返回注册结果，成功返回提示信息，失败返回对应错误信息
     */
    @PostMapping("/register")
    public Result<Long> register(@RequestBody CourseRegisterDTO registerDTO) {
        try {
            Long res = courseService.register(registerDTO);
            if (res == null) {
                return Result.error(500, "添加失败");
            } else if (res == -1L) {
                return Result.error(400, "课程已存在");
            }
            return Result.success(res);
        } catch (RuntimeException ex) {
            return Result.error(500, "添加失败");
        }
    }

    @DeleteMapping("/delete")
    public Result<String> delete(@RequestBody CourseDeleteDTO deleteDTO) {
        Integer deleted = courseService.removeCourses(deleteDTO.getCourseIds());
        if (deleted != null && deleted > 0) {
            return Result.success("删除成功");
        } else if (deleted == -1) {
            return Result.error(400, "课程不存在");
        } else {
            return Result.error(500, "删除失败");
        }
    }

    /**
     * 课程更新
     */
    @PutMapping("/update")
    public Result<String> update(@RequestBody CourseUpdateDTO updateDTO) {
        try {
            boolean updated = courseService.update(updateDTO);
            if (!updated) {
                return Result.error(404, "课程不存在");
            }
            return Result.success("更新成功");
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.error(500, "更新失败");
        }
    }

    /**
     * 上传课程视频并绑定课程
     */
    @PostMapping("/{courseId}/video")
    public Result<CourseVideoUploadVO> uploadCourseVideo(@PathVariable Long courseId,
            @RequestParam("file") MultipartFile file) {
        CourseDetailVO course = courseService.getCourseByIdForUser(courseId);
        if (course == null) {
            return Result.error(404, "课程不存在");
        }
        try {
            String relativePath = videoService.uploadAndBindCourseVideo(file, course);
            CourseVideoUploadVO vo = new CourseVideoUploadVO();
            vo.setCourseId(courseId);
            vo.setVideoPath(relativePath);
            vo.setVideoUrl(videoBaseUrl + "/videos/" + relativePath);
            vo.setDurationSeconds(videoService.getVideoDurationInSeconds(courseId));

            courseService.updateCourseStatus(courseId, CourseStatus.ONLINE.getCode()); // 视频上传成功后将课程状态改为上线
            courseService.updateCourseDuration(courseId, vo.getDurationSeconds()); // 同步更新课程时长

            return Result.success("上传成功", vo);
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.error(500, "上传失败");
        }
    }

    @GetMapping("/detail/{courseId}")
    public Result<CourseUpdateVO> getCourseDetail(@PathVariable Long courseId) {
        CourseUpdateVO courseUpdateVO = courseService.getCourseDetailForAdmin(courseId);
        if (courseUpdateVO == null) {
            return Result.error(404, "课程不存在");
        }
        return Result.success(courseUpdateVO);
    }

    /**
    * 课程上下架
    * @param courseId 课程 ID
    * @param status 目标状态（0=草稿，1=上线，2=下架）
    * @return 操作结果，成功返回提示信息，失败返回对应错误信息
    */
    @PutMapping("/status/{courseId}")
    public Result<String> updateCourseStatus(@PathVariable Long courseId, @RequestParam Integer status) {
        try {
            boolean updated = courseService.updateCourseStatus(courseId, status);
            if (!updated) {
                return Result.error(404, "课程不存在");
            }
            return Result.success("操作成功");
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.error(500, "操作失败");
        }
    }

}
