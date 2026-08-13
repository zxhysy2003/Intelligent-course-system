package com.sy.course_system.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;

class JwtUtilTest {

    private static final String FIRST_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String SECOND_KEY = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";

    @Test
    void signsAndParsesTokensWithConfiguredKey() {
        JwtUtil jwtUtil = new JwtUtil(FIRST_KEY);

        String token = jwtUtil.generateToken(Map.of(
                "userId", 7L,
                "username", "tester",
                "role", "STUDENT"));

        assertEquals(7L, ((Number) jwtUtil.parseToken(token).get("userId")).longValue());
    }

    @Test
    void rejectsInvalidOrShortSecrets() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil("***"));
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil("c2hvcnQ="));
    }

    @Test
    void rotatedKeyRejectsTokensSignedWithPreviousKey() {
        String token = new JwtUtil(FIRST_KEY).generateToken(Map.of("userId", 7L));

        assertThrows(JwtException.class, () -> new JwtUtil(SECOND_KEY).parseToken(token));
    }
}
