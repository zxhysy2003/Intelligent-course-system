package com.sy.course_system.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AgentPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void shouldBindAgentPropertiesFromKebabCaseProperties() {
        contextRunner
                .withPropertyValues(
                        "agent.enabled=true",
                        "agent.provider=openai-compatible",
                        "agent.base-url=https://llm.example.test/v1",
                        "agent.api-key=test-key",
                        "agent.model=test-model",
                        "agent.connect-timeout-ms=1500",
                        "agent.read-timeout-ms=30000",
                        "agent.max-history-messages=8",
                        "agent.max-context-courses=3",
                        "agent.max-output-tokens=600",
                        "agent.temperature=0.2")
                .run(context -> {
                    AgentProperties properties = context.getBean(AgentProperties.class);

                    assertEquals("openai-compatible", properties.provider());
                    assertEquals("https://llm.example.test/v1", properties.baseUrl());
                    assertEquals("test-key", properties.apiKey());
                    assertEquals("test-model", properties.model());
                    assertEquals(1500, properties.connectTimeoutMs());
                    assertEquals(30000, properties.readTimeoutMs());
                    assertEquals(8, properties.maxHistoryMessages());
                    assertEquals(3, properties.maxContextCourses());
                    assertEquals(600, properties.maxOutputTokens());
                    assertEquals(0.2d, properties.temperature());
                    assertFalse(properties.useMockClient());
                });
    }

    @Test
    void applicationYamlShouldExposeAgentPropertiesAtTopLevel() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(
                        "AGENT_LLM_API_KEY=test-key",
                        "AGENT_LLM_BASE_URL=https://llm.example.test/v1",
                        "AGENT_LLM_MODEL=test-model")
                .run(context -> {
                    AgentProperties properties = context.getBean(AgentProperties.class);

                    assertEquals("test-key", properties.apiKey());
                    assertEquals("https://llm.example.test/v1", properties.baseUrl());
                    assertEquals("test-model", properties.model());
                    assertFalse(properties.useMockClient());
                });
    }

    @Configuration
    @EnableConfigurationProperties(AgentProperties.class)
    static class TestConfig {
    }
}
