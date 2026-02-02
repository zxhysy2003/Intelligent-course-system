package com.sy.course_system.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.UserInfo;
import com.sy.course_system.common.util.JwtUtil;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.Nullable;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final String AUTH_COOKIE_NAME = "auth_token";

    // JWT拦截器的实现可以在这里添加
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 在这里实现JWT验证逻辑

        // 如果是跨域预检请求，直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = null;

        // 1. 优先从Header获取token
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } 

        // 2. 如果Header中没有，再从Cookie获取token
        if (token == null) {
            Cookie cookie = WebUtils.getCookie(request, AUTH_COOKIE_NAME);
            if (cookie != null) {
                token = cookie.getValue();
            }
        }

        // 3. 如果token仍然为空，拒绝请求
        if (token == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("缺少Token，未授权访问");
            return false;
        }

        // 4. 验证token
        try {
            // 1.解析 token
            Claims claims = JwtUtil.parseToken(token);

            // 2. 可以将用户信息存储在请求属性中，供后续使用
            Number userIdNum = (Number) claims.get("userId");
            Long userId = userIdNum.longValue();
            String username = (String) claims.get("username");
            String role = (String) claims.get("role");
            
            UserInfo userInfo = new UserInfo(userId, username, role);
            UserContext.set(userInfo);
            
        } catch (ExpiredJwtException e) {
            response.setStatus(401);
            response.getWriter().write("Token无效或已过期");
            return false;
        } catch (JwtException e) {
            response.setStatus(401);
            response.getWriter().write("Token无效");
            return false;
        }

        return true; // 放行
    }

    @Override
    public void afterCompletion(HttpServletRequest request, 
                                HttpServletResponse response, 
                                Object handler, 
                                @Nullable Exception ex)
            throws Exception {
        // 清理线程变量，防止内存泄漏
        UserContext.clear();
    }
}
