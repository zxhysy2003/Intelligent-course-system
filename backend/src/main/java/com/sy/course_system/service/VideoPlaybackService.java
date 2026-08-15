package com.sy.course_system.service;

import org.springframework.stereotype.Service;

import com.sy.course_system.vo.CoursePlaybackVO;

@Service
public class VideoPlaybackService {

    private final VideoService videoService;
    private final PlaybackTokenService playbackTokenService;

    public VideoPlaybackService(VideoService videoService, PlaybackTokenService playbackTokenService) {
        this.videoService = videoService;
        this.playbackTokenService = playbackTokenService;
    }

    public CoursePlaybackVO issue(Long userId, Long courseId) {
        VideoPlaybackSource source = videoService.getPlaybackSource(courseId);
        if (source == null || source.videoPath() == null || source.videoPath().isBlank()) {
            return null;
        }

        PlaybackTokenService.PlaybackGrant grant = playbackTokenService.issue(
                userId,
                courseId,
                source.videoPath(),
                source.durationSeconds());
        return new CoursePlaybackVO(grant.playbackUrl(), grant.expiresAt());
    }
}
