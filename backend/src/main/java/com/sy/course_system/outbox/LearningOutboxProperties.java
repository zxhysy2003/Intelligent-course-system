package com.sy.course_system.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("learning.outbox")
public record LearningOutboxProperties(@DefaultValue("true") boolean enabled,
        @DefaultValue("4") int parallelism, @DefaultValue("60") int leaseSeconds,
        @DefaultValue("20") int renewSeconds, @DefaultValue("20") int maxAttempts,
        @DefaultValue("5") int retryBaseSeconds, @DefaultValue("300") int retryMaxSeconds) {
    public LearningOutboxProperties {
        if (parallelism < 1 || renewSeconds < 1 || leaseSeconds < renewSeconds * 2
                || maxAttempts < 1 || retryBaseSeconds < 1 || retryMaxSeconds < retryBaseSeconds) {
            throw new IllegalArgumentException("无效的 learning.outbox 配置");
        }
    }
}
