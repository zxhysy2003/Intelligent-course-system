package com.sy.course_system.service;

import org.springframework.web.multipart.MultipartFile;

import com.sy.course_system.vo.CourseDetailVO;

public interface VideoService {
    Integer getVideoDurationInSeconds(Long courseId);

    String getVideoPath(Long courseId);

    String uploadAndBindCourseVideo(MultipartFile file, CourseDetailVO courseDetailVO);
}
