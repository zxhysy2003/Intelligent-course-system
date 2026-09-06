package com.sy.course_system.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RedisPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TestConfig.class);

    @Test
    void applicationYamlShouldSelectLettuceAndUseFailFastTimeouts() {
        contextRunner.run(context -> {
            RedisProperties properties = context.getBean(RedisProperties.class);

            assertEquals(RedisProperties.ClientType.LETTUCE, properties.getClientType());
            assertEquals(Duration.ofMillis(500), properties.getConnectTimeout());
            assertEquals(Duration.ofMillis(500), properties.getTimeout());
        });
    }

    @Test
    void redisTimeoutsShouldRemainEnvironmentConfigurable() {
        contextRunner
                .withPropertyValues(
                        "REDIS_CONNECT_TIMEOUT=750ms",
                        "REDIS_COMMAND_TIMEOUT=900ms")
                .run(context -> {
                    RedisProperties properties = context.getBean(RedisProperties.class);

                    assertEquals(Duration.ofMillis(750), properties.getConnectTimeout());
                    assertEquals(Duration.ofMillis(900), properties.getTimeout());
                });
    }

    @Configuration
    @EnableConfigurationProperties(RedisProperties.class)
    static class TestConfig {
    }
}
