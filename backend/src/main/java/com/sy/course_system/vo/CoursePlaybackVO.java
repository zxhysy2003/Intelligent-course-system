package com.sy.course_system.vo;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonFormat;

public class CoursePlaybackVO {
    private String playbackUrl;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant expiresAt;

    public CoursePlaybackVO() {
    }

    public CoursePlaybackVO(String playbackUrl, Instant expiresAt) {
        this.playbackUrl = playbackUrl;
        this.expiresAt = expiresAt;
    }

    public String getPlaybackUrl() {
        return playbackUrl;
    }

    public void setPlaybackUrl(String playbackUrl) {
        this.playbackUrl = playbackUrl;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
