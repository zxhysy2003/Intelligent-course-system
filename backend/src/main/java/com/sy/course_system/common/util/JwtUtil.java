package com.sy.course_system.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

import java.util.Date;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
    // 过期时间： 24小时
    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000;
    private static final int MINIMUM_KEY_BYTES = 32;

    private final SecretKey key;

    public JwtUtil(@Value("${app.jwt.secret-base64}") String secretBase64) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secretBase64);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("JWT_SECRET_BASE64 必须是有效的 Base64 字符串", ex);
        }
        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException("JWT_SECRET_BASE64 解码后至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    // 生成 Token
    public String generateToken(Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + EXPIRATION_TIME))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // 解析 Token
    public Claims parseToken(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

}
