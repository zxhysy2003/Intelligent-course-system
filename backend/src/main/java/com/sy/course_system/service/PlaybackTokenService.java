package com.sy.course_system.service;

import java.io.File;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class PlaybackTokenService {

    public static final String PLAYBACK_AUTHORITY = "VIDEO_PLAYBACK";

    private static final String ISSUER = "course-system";
    private static final String AUDIENCE = "video-playback";
    private static final int MINIMUM_KEY_BYTES = 32;

    private final SecretKey key;
    private final Duration minimumTtl;
    private final Duration durationGrace;

    public PlaybackTokenService(
            @Value("${app.playback-token.secret-base64}") String secretBase64,
            @Value("${app.jwt.secret-base64}") String jwtSecretBase64,
            @Value("${app.playback-token.minimum-ttl:PT2H}") Duration minimumTtl,
            @Value("${app.playback-token.duration-grace:PT30M}") Duration durationGrace) {
        byte[] keyBytes;
        byte[] jwtKeyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secretBase64);
            jwtKeyBytes = Decoders.BASE64.decode(jwtSecretBase64);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("JWT 与播放凭证密钥必须是有效的 Base64 字符串", ex);
        }
        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException("PLAYBACK_TOKEN_SECRET_BASE64 解码后至少需要 32 字节");
        }
        if (MessageDigest.isEqual(keyBytes, jwtKeyBytes)) {
            throw new IllegalArgumentException("PLAYBACK_TOKEN_SECRET_BASE64 不能复用 JWT_SECRET_BASE64");
        }
        if (minimumTtl.isNegative() || minimumTtl.isZero()
                || durationGrace.isNegative()) {
            throw new IllegalArgumentException("播放凭证有效期配置不合法");
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.minimumTtl = minimumTtl;
        this.durationGrace = durationGrace;
    }

    public PlaybackGrant issue(Long userId, Long courseId, String videoPath, Integer durationSeconds) {
        if (userId == null || courseId == null) {
            throw new IllegalArgumentException("播放凭证缺少用户或课程信息");
        }

        String publicPath = normalizePublicPath(videoPath);
        long duration = durationSeconds == null ? 0L : Math.max(0L, durationSeconds.longValue());
        Duration ttl = Duration.ofSeconds(duration).plus(durationGrace);
        if (ttl.compareTo(minimumTtl) < 0) {
            ttl = minimumTtl;
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);
        String token = Jwts.builder()
                // iat/exp 以秒编码；随机 jti 确保同一秒内续签也会生成不同 URL，触发播放器重新加载。
                .setId(UUID.randomUUID().toString())
                .setIssuer(ISSUER)
                .setAudience(AUDIENCE)
                .setSubject(publicPath)
                .claim("userId", userId)
                .claim("courseId", courseId)
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(expiresAt))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        String playbackUrl = UriComponentsBuilder.fromPath(publicPath)
                .queryParam("token", token)
                .build()
                .toUriString();
        return new PlaybackGrant(playbackUrl, expiresAt);
    }

    public PlaybackPrincipal validate(String token, String requestPath) throws JwtException {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build()
                .parseClaimsJws(token)
                .getBody();

        String protectedPath = claims.getSubject();
        Object userIdClaim = claims.get("userId");
        Object courseIdClaim = claims.get("courseId");
        if (protectedPath == null
                || !protectedPath.equals(requestPath)
                || !(userIdClaim instanceof Number userId)
                || !(courseIdClaim instanceof Number courseId)) {
            throw new JwtException("播放凭证与请求资源不匹配");
        }

        return new PlaybackPrincipal(userId.longValue(), courseId.longValue(), protectedPath);
    }

    private String normalizePublicPath(String videoPath) {
        if (videoPath == null || videoPath.isBlank()) {
            throw new IllegalArgumentException("课程视频路径不能为空");
        }

        String relativePath = videoPath.trim();
        while (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        if (relativePath.isBlank()
                || relativePath.contains("\\")
                || relativePath.contains("?")
                || relativePath.contains("#")) {
            throw new IllegalArgumentException("课程视频路径不合法");
        }
        for (String segment : relativePath.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("课程视频路径不合法");
            }
        }

        Path normalizedPath = Path.of(relativePath).normalize();
        if (normalizedPath.isAbsolute() || normalizedPath.startsWith("..")) {
            throw new IllegalArgumentException("课程视频路径不合法");
        }

        String normalized = normalizedPath.toString().replace(File.separatorChar, '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (!filename.contains(".")) {
            normalized += ".mp4";
        }
        return "/videos/" + normalized;
    }

    public record PlaybackGrant(String playbackUrl, Instant expiresAt) {
    }

    public record PlaybackPrincipal(Long userId, Long courseId, String videoPath) {
    }
}
