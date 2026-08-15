package com.sy.course_system.config;

import java.io.IOException;
import java.util.List;

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

import com.sy.course_system.service.PlaybackTokenService;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class PlaybackTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final RequestMatcher VIDEO_READ_REQUESTS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/videos/**"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.HEAD, "/videos/**"));

    private final PlaybackTokenService playbackTokenService;

    public PlaybackTokenAuthenticationFilter(PlaybackTokenService playbackTokenService) {
        this.playbackTokenService = playbackTokenService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !VIDEO_READ_REQUESTS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String[] tokenValues = request.getParameterValues("token");
        if (tokenValues == null || tokenValues.length != 1 || tokenValues[0].isBlank()) {
            writeUnauthorized(response, "缺少视频播放凭证");
            return;
        }

        PlaybackTokenService.PlaybackPrincipal principal;
        try {
            principal = playbackTokenService.validate(tokenValues[0], requestPath(request));
        } catch (ExpiredJwtException ex) {
            writeUnauthorized(response, "视频播放凭证无效或已过期");
            return;
        } catch (JwtException | IllegalArgumentException ex) {
            writeUnauthorized(response, "视频播放凭证无效");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority(PlaybackTokenService.PLAYBACK_AUTHORITY)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        filterChain.doFilter(request, response);
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getWriter().write(message);
    }
}
