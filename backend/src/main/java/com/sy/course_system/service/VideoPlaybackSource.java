package com.sy.course_system.service;

/**
 * 签发播放凭证所需的视频元数据。
 */
public record VideoPlaybackSource(String videoPath, Integer durationSeconds) {
}
