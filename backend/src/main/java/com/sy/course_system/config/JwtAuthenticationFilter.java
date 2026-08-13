package com.sy.course_system.config;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import com.sy.course_system.common.ApiPaths;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.UserInfo;
import com.sy.course_system.common.util.JwtUtil;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_COOKIE_NAME = "auth_token";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final RequestMatcher PUBLIC_REQUESTS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(ApiPaths.AUTH + "/login"),
            PathPatternRequestMatcher.withDefaults().matcher(ApiPaths.AUTH + "/register"),
            PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui/**"),
            PathPatternRequestMatcher.withDefaults().matcher("/swagger-ui.html"),
            PathPatternRequestMatcher.withDefaults().matcher("/v3/api-docs/**"));
    private static final RequestMatcher VIDEO_READ_REQUESTS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/videos/**"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.HEAD, "/videos/**"));

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        UserContext.clear();
        try {
            String token = resolveToken(request);
            if (token == null) {
                filterChain.doFilter(request, response);
                return;
            }

            UserInfo userInfo;
            try {
                userInfo = parseUserInfo(token);
            } catch (ExpiredJwtException ex) {
                writeUnauthorized(response, "Token无效或已过期");
                return;
            } catch (JwtException | IllegalArgumentException ex) {
                writeUnauthorized(response, "Token无效");
                return;
            }

            String authority = "ROLE_" + userInfo.getRole().toUpperCase(Locale.ROOT);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userInfo,
                    null,
                    List.of(new SimpleGrantedAuthority(authority)));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);
            UserContext.set(userInfo);

            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        if (PUBLIC_REQUESTS.matches(request)) {
            return null;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }

        if (!VIDEO_READ_REQUESTS.matches(request)) {
            return null;
        }
        Cookie cookie = WebUtils.getCookie(request, AUTH_COOKIE_NAME);
        return cookie != null ? cookie.getValue() : null;
    }

    private UserInfo parseUserInfo(String token) {
        Claims claims = jwtUtil.parseToken(token);
        Object userIdClaim = claims.get("userId");
        String username = claims.get("username", String.class);
        String role = claims.get("role", String.class);

        if (!(userIdClaim instanceof Number userIdNumber)
                || username == null || username.isBlank()
                || role == null || role.isBlank()) {
            throw new MalformedJwtException("Token缺少必要的用户声明");
        }

        return new UserInfo(userIdNumber.longValue(), username, role.trim());
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getWriter().write(message);
    }
}
