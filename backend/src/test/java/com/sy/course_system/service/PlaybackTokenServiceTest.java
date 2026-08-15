package com.sy.course_system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;

class PlaybackTokenServiceTest {

    private static final String JWT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String PLAYBACK_SECRET = "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=";

    @Test
    void bindsTokenToExactVideoPathAndCarriesPlaybackIdentity() {
        PlaybackTokenService service = service();

        PlaybackTokenService.PlaybackGrant grant = service.issue(3L, 7L, "7/sample.mp4", 60);
        String token = tokenFrom(grant.playbackUrl());
        PlaybackTokenService.PlaybackPrincipal principal = service.validate(token, "/videos/7/sample.mp4");

        assertEquals(3L, principal.userId());
        assertEquals(7L, principal.courseId());
        assertThrows(JwtException.class, () -> service.validate(token, "/videos/8/sample.mp4"));
    }

    @Test
    void usesVideoDurationPlusGraceWhenItExceedsMinimumTtl() {
        PlaybackTokenService service = service();
        Instant beforeIssue = Instant.now();

        PlaybackTokenService.PlaybackGrant grant = service.issue(3L, 7L, "sample.mp4", 10_800);

        assertTrue(Duration.between(beforeIssue, grant.expiresAt()).compareTo(Duration.ofMinutes(210)) >= 0);
    }

    @Test
    void issuesDifferentUrlsForImmediateRenewalOfTheSameVideo() {
        PlaybackTokenService service = service();

        String firstUrl = service.issue(3L, 7L, "7/sample.mp4", 60).playbackUrl();
        String renewedUrl = service.issue(3L, 7L, "7/sample.mp4", 60).playbackUrl();

        assertNotEquals(firstUrl, renewedUrl);
    }

    @Test
    void rejectsReusingLoginJwtSecret() {
        assertThrows(IllegalArgumentException.class, () -> new PlaybackTokenService(
                JWT_SECRET,
                JWT_SECRET,
                Duration.ofHours(2),
                Duration.ofMinutes(30)));
    }

    private PlaybackTokenService service() {
        return new PlaybackTokenService(
                PLAYBACK_SECRET,
                JWT_SECRET,
                Duration.ofHours(2),
                Duration.ofMinutes(30));
    }

    private String tokenFrom(String playbackUrl) {
        return playbackUrl.substring(playbackUrl.indexOf("?token=") + "?token=".length());
    }
}
