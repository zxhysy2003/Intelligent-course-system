package com.sy.course_system.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.util.JwtUtil;

class JwtInterceptorTest {
    private final JwtInterceptor interceptor = new JwtInterceptor();

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void rejectsMissingTokenWithUnauthorized() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courses/search");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
        assertNull(UserContext.get());
    }

    @Test
    void rejectsStudentFromAdminApiAndClearsContext() throws Exception {
        MockHttpServletRequest request = authorizedRequest("STUDENT", "/api/v1/admin/users/search");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
        assertNull(UserContext.get());
    }

    @Test
    void allowsAdminToAccessAdminApi() throws Exception {
        MockHttpServletRequest request = authorizedRequest("ADMIN", "/api/v1/admin/courses/search");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals("ADMIN", UserContext.getRole());
    }

    @Test
    void allowsStudentToAccessLearnerApi() throws Exception {
        MockHttpServletRequest request = authorizedRequest("STUDENT", "/api/v1/courses/search");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(1L, UserContext.getUserId());
    }

    private MockHttpServletRequest authorizedRequest(String role, String path) {
        String token = JwtUtil.generateToken(Map.of(
                "userId", 1L,
                "username", "tester",
                "role", role));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
