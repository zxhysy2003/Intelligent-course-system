package com.sy.course_system.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 学习助手配置。
 *
 * 默认允许无密钥本地运行：当 apiKey 为空或 provider=mock 时，后端返回本地 mock
 * 回答，便于开发和演示；生产环境应通过环境变量注入真实模型配置。
 */
@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("openai-compatible") String provider,
        @DefaultValue("https://api.openai.com/v1") String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("gpt-4o-mini") String model,
        @DefaultValue("5000") int connectTimeoutMs,
        @DefaultValue("45000") int readTimeoutMs,
        @DefaultValue("12") int maxHistoryMessages,
        @DefaultValue("5") int maxContextCourses,
        @DefaultValue("5000") int contextRecommendTimeoutMs,
        @DefaultValue("800") int maxOutputTokens,
        @DefaultValue("0.3") double temperature) {

    public boolean useMockClient() {
        return "mock".equalsIgnoreCase(provider) || apiKey == null || apiKey.isBlank();
    }
}
