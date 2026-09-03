package com.sy.course_system.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.sy.course_system.common.ApiPaths;
import com.sy.course_system.service.PlaybackTokenService;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PlaybackTokenAuthenticationFilter playbackTokenAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
            PlaybackTokenAuthenticationFilter playbackTokenAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.playbackTokenAuthenticationFilter = playbackTokenAuthenticationFilter;
    }

    /**
     * JWT 过滤器只由 Spring Security 管理，避免 Spring Boot 将 Filter Bean 再注册到 Servlet 容器。
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<PlaybackTokenAuthenticationFilter> playbackTokenAuthenticationFilterRegistration() {
        FilterRegistrationBean<PlaybackTokenAuthenticationFilter> registration =
                new FilterRegistrationBean<>(playbackTokenAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "缺少Token，未授权访问"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, "无管理员权限")))
                .authorizeHttpRequests(authorize -> authorize
                        // 只放行容器内部的错误转发，使原始异常能由 Spring Boot 的错误处理链生成响应。
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(ApiPaths.AUTH + "/login", ApiPaths.AUTH + "/register").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // 健康检查供本地编排和探针匿名使用；其余管理端点可能包含运行信息，仅管理员可见。
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator", "/actuator/**").hasRole("ADMIN")
                        .requestMatchers(ApiPaths.ADMIN, ApiPaths.ADMIN + "/**").hasRole("ADMIN")
                        .requestMatchers(ApiPaths.API_V1, ApiPaths.API_V1 + "/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/videos/**")
                        .hasAuthority(PlaybackTokenService.PLAYBACK_AUTHORITY)
                        .requestMatchers(HttpMethod.HEAD, "/videos/**")
                        .hasAuthority(PlaybackTokenService.PLAYBACK_AUTHORITY)
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(playbackTokenAuthenticationFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getWriter().write(message);
    }
}
