package com.sy.course_system.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import java.security.Key;
import java.util.Date;
import java.util.Map;

public class JwtUtil {
    // 密钥
    private static final String SECRET_KEY = "course-system-secret-key-course-system";

    // 过期时间： 24小时
    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000;

    private static final Key KEY = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());

    // 生成 Token
    public static String generateToken(Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + EXPIRATION_TIME))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    // 解析 Token
    public static Claims parseToken(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

}
