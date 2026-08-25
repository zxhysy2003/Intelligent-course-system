package com.sy.course_system.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class LearningBehaviorIdempotencySqlTest {

    @Test
    void migrationShouldAddCaseSensitiveUserEventUniqueKey() throws IOException {
        String migration = readClasspath("db/migration/V3__add_learning_behavior_event_id.sql")
                .replaceAll("\\s+", " ")
                .trim();

        assertTrue(migration.contains("event_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL"));
        assertTrue(migration.contains(
                "UNIQUE INDEX `uk_learning_behavior_user_event` (`user_id`, `event_id`)"));
    }

    @Test
    void mapperShouldClaimBeforeProcessingAndUseCurrentReadForReplay() throws NoSuchMethodException {
        Insert insert = LearningBehaviorMapper.class
                .getMethod("insertStudyIfAbsent", com.sy.course_system.entity.LearningBehavior.class)
                .getAnnotation(Insert.class);
        Select select = LearningBehaviorMapper.class
                .getMethod("selectByUserIdAndEventIdForShare", Long.class, String.class)
                .getAnnotation(Select.class);

        String insertSql = String.join(" ", Arrays.asList(insert.value())).replaceAll("\\s+", " ");
        String selectSql = String.join(" ", Arrays.asList(select.value())).replaceAll("\\s+", " ");

        assertTrue(insertSql.contains("INSERT IGNORE INTO learning_behavior"));
        assertTrue(insertSql.contains("user_id, course_id, event_id, behavior_type, duration"));
        assertTrue(selectSql.contains("user_id = #{userId}"));
        assertTrue(selectSql.contains("event_id = #{eventId}"));
        assertTrue(selectSql.contains("FOR SHARE"));
    }

    private String readClasspath(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
