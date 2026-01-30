package com.sy.course_system.service;

public interface VideoService {
    Integer getVideoDurationInSeconds(Long courseId);

    String getVideoPath(Long courseId);
}
