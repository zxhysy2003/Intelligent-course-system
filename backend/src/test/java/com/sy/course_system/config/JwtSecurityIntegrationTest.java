package com.sy.course_system.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.util.JwtUtil;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers = JwtSecurityIntegrationTest.SecurityProbeController.class)
@ContextConfiguration(classes = {
        JwtSecurityIntegrationTest.SecurityProbeController.class,
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtUtil.class,
        CorConfig.class,
        WebConfig.class
})
@ImportAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
class JwtSecurityIntegrationTest {

    private static final String TEST_SECRET_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String ALLOWED_ORIGIN = "http://allowed.test";
    private static final Path VIDEO_DIRECTORY = createVideoDirectory();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private SecurityProbeController probeController;
    @Autowired
    private FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret-base64", () -> TEST_SECRET_BASE64);
        registry.add("app.upload.video-dir", VIDEO_DIRECTORY::toString);
        registry.add("app.cors.allowed-origin-patterns", () -> ALLOWED_ORIGIN);
    }

    @BeforeEach
    void setUp() {
        probeController.adminInvocations.set(0);
        UserContext.clear();
    }

    @Test
    void disablesServletContainerRegistrationForJwtFilter() {
        assertFalse(jwtAuthenticationFilterRegistration.isEnabled());
    }

    @AfterAll
    static void cleanUpVideoDirectory() throws IOException {
        Files.deleteIfExists(VIDEO_DIRECTORY.resolve("sample.mp4"));
        Files.deleteIfExists(VIDEO_DIRECTORY);
    }

    @Test
    void allowsPublicAuthenticationEndpointsWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login"))
                .andExpect(status().isOk())
                .andExpect(content().string("login"));
        mockMvc.perform(post("/api/v1/auth/register"))
                .andExpect(status().isOk())
                .andExpect(content().string("register"));
    }

    @Test
    void ignoresStaleAuthorizationHeaderOnPublicAuthenticationEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken()))
                .andExpect(status().isOk())
                .andExpect(content().string("login"));
    }

    @Test
    void rejectsMissingTokenFromBusinessApi() throws Exception {
        mockMvc.perform(get("/api/v1/courses/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("缺少Token，未授权访问"));
    }

    @Test
    void allowsInternalErrorDispatchWithoutReauthentication() throws Exception {
        mockMvc.perform(get("/api/v1/courses/probe")
                        .with(request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(content().string("null:null"));
    }

    @Test
    void doesNotExposeErrorPathToNormalRequests() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exposesAuthenticatedUserThroughCompatibilityContextAndClearsItAfterRequest() throws Exception {
        mockMvc.perform(get("/api/v1/courses/probe")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().string("1:STUDENT"));

        assertNull(UserContext.get());
    }

    @Test
    void doesNotConvertBusinessExceptionsIntoAuthenticationFailures() throws Exception {
        mockMvc.perform(get("/api/v1/courses/business-error")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("STUDENT")))
                .andExpect(status().isBadRequest());

        assertNull(UserContext.get());
    }

    @Test
    void rejectsNonAdminRolesFromAdminApi() throws Exception {
        mockMvc.perform(post("/api/v1/admin/courses/search")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(content().string("无管理员权限"));
        mockMvc.perform(post("/api/v1/admin/courses/search")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("TEACHER")))
                .andExpect(status().isForbidden());

        assertEquals(0, probeController.adminInvocations.get());
    }

    @Test
    void allowsAdminRoleToAccessAdminApi() throws Exception {
        mockMvc.perform(post("/api/v1/admin/courses/search")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string("admin"));

        assertEquals(1, probeController.adminInvocations.get());
    }

    @Test
    void rejectsMatrixParameterAdminPathBeforeController() throws Exception {
        mockMvc.perform(post("/api/v1/admin;x=1/courses/search")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken("STUDENT")))
                .andExpect(status().isBadRequest());

        assertEquals(0, probeController.adminInvocations.get());
    }

    @Test
    void rejectsInvalidExpiredAndIncompleteTokens() throws Exception {
        mockMvc.perform(get("/api/v1/courses/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Token无效"));
        mockMvc.perform(get("/api/v1/courses/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Token无效或已过期"));
        mockMvc.perform(get("/api/v1/courses/probe")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtil.generateToken(Map.of(
                                "userId", 1L,
                                "username", "tester"))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Token无效"));
    }

    @Test
    void acceptsBearerOrCookieForVideoAndPreservesRangeRequests() throws Exception {
        String token = token("STUDENT");

        mockMvc.perform(get("/videos/sample.mp4")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().bytes("0123456789".getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(get("/videos/sample.mp4")
                        .cookie(new Cookie("auth_token", token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/videos/sample.mp4")
                        .header(HttpHeaders.RANGE, "bytes=0-3")
                        .cookie(new Cookie("auth_token", token)))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-3/10"))
                .andExpect(content().bytes("0123".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void doesNotUseVideoCookieForBusinessApi() throws Exception {
        String token = token("STUDENT");

        mockMvc.perform(get("/api/v1/courses/probe")
                        .cookie(new Cookie("auth_token", token)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/videos/sample.mp4"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void appliesConfiguredCorsRulesBeforeAuthentication() throws Exception {
        mockMvc.perform(options("/api/v1/courses/probe")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));

        mockMvc.perform(options("/api/v1/courses/probe")
                        .header(HttpHeaders.ORIGIN, "http://not-allowed.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private String bearerToken(String role) {
        return "Bearer " + token(role);
    }

    private String token(String role) {
        return jwtUtil.generateToken(Map.of(
                "userId", 1L,
                "username", "tester",
                "role", role));
    }

    private String expiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET_BASE64));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(Map.of("userId", 1L, "username", "tester", "role", "STUDENT"))
                .setIssuedAt(new Date(now - 2_000))
                .setExpiration(new Date(now - 1_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private static Path createVideoDirectory() {
        try {
            Path directory = Files.createTempDirectory("course-system-video-test-");
            Files.writeString(directory.resolve("sample.mp4"), "0123456789", StandardCharsets.UTF_8);
            return directory;
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @RestController
    static class SecurityProbeController {
        private final AtomicInteger adminInvocations = new AtomicInteger();

        @PostMapping("/api/v1/auth/login")
        String login() {
            return "login";
        }

        @PostMapping("/api/v1/auth/register")
        String register() {
            return "register";
        }

        @GetMapping("/api/v1/courses/probe")
        String learnerApi() {
            return UserContext.getUserId() + ":" + UserContext.getRole();
        }

        @GetMapping("/api/v1/courses/business-error")
        String businessError() {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "business error");
        }

        @PostMapping("/api/v1/admin/courses/search")
        String adminApi() {
            adminInvocations.incrementAndGet();
            return "admin";
        }
    }
}
