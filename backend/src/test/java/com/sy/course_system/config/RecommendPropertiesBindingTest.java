package com.sy.course_system.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class RecommendPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void shouldBindKebabCaseRecommendPropertiesToRecords() {
        contextRunner
                .withPropertyValues(
                        "recommend.new-course.inject-slots=1,4,9",
                        "recommend.new-course.enabled=false",
                        "recommend.async.enabled=false",
                        "recommend.cache.wait-retry-times=5",
                        "recommend.cache.score-matrix-wait-millis=120",
                        "recommend.regular.request-top-n=60")
                .run(context -> {
                    RecommendProperties properties = context.getBean(RecommendProperties.class);

                    assertEquals(List.of(1, 4, 9), properties.newCourse().injectSlots());
                    assertFalse(properties.newCourse().enabled());
                    assertFalse(properties.async().enabled());
                    assertEquals(5, properties.cache().waitRetryTimes());
                    assertEquals(120L, properties.cache().scoreMatrixWaitMillis());
                    assertEquals(60, properties.regular().requestTopN());
                });
    }

    @Configuration
    @EnableConfigurationProperties(RecommendProperties.class)
    static class TestConfig {
    }
}
