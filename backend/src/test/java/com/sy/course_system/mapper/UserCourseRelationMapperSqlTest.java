package com.sy.course_system.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class UserCourseRelationMapperSqlTest {

    @Test
    void atomicProgressUpdateShouldNotApplyDurationTwice() throws IOException {
        String updateSql = readUpdateSql("addStudyTimeAndUpdateProgress")
                .replaceAll("\\s+", " ")
                .trim();

        String learnedSecondsAssignment =
                "learned_seconds = LEAST(learned_seconds + #{duration}, #{totalSeconds})";
        String progressAssignment =
                "progress = LEAST(100, (learned_seconds * 100) DIV #{totalSeconds})";

        assertTrue(updateSql.contains(learnedSecondsAssignment));
        assertTrue(updateSql.contains(progressAssignment));
        assertTrue(updateSql.contains("WHEN learned_seconds &gt;= #{totalSeconds}"));
        assertTrue(updateSql.indexOf(learnedSecondsAssignment) < updateSql.indexOf(progressAssignment));
        assertEquals(1, countOccurrences(updateSql, "learned_seconds + #{duration}"));
    }

    private String readUpdateSql(String updateId) throws IOException {
        ClassPathResource resource = new ClassPathResource("mapper/UserCourseRelationMapper.xml");
        String xml;
        try (InputStream input = resource.getInputStream()) {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String startToken = "<update id=\"" + updateId + "\">";
        int start = xml.indexOf(startToken);
        int end = xml.indexOf("</update>", start);
        assertTrue(start >= 0 && end > start, "找不到 Mapper update: " + updateId);
        return xml.substring(start, end);
    }

    private int countOccurrences(String text, String target) {
        int count = 0;
        int fromIndex = 0;
        while ((fromIndex = text.indexOf(target, fromIndex)) >= 0) {
            count++;
            fromIndex += target.length();
        }
        return count;
    }
}
