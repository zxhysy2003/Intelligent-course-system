package com.sy.course_system.recommend.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class LearningGoalRuleSupportTest {

    @Test
    void goalLabelShouldMapKnownGoalsAndFallbackToFoundationLabel() {
        assertEquals("打基础", LearningGoalRuleSupport.goalLabel("FOUNDATION"));
        assertEquals("备考", LearningGoalRuleSupport.goalLabel("EXAM"));
        assertEquals("做项目", LearningGoalRuleSupport.goalLabel("PROJECT"));
        assertEquals("找工作", LearningGoalRuleSupport.goalLabel("JOB"));
        assertEquals("打基础", LearningGoalRuleSupport.goalLabel(null));
        assertEquals("打基础", LearningGoalRuleSupport.goalLabel(" "));
        assertEquals("打基础", LearningGoalRuleSupport.goalLabel("UNKNOWN"));
    }

    @Test
    void isGoalFitShouldMatchFoundationAndExamByDifficultyOrKeywords() {
        assertTrue(LearningGoalRuleSupport.isGoalFit("FOUNDATION", 1, "任意课程", List.of()));
        assertTrue(LearningGoalRuleSupport.isGoalFit("EXAM", 3, "计算机原理", List.of()));
        assertTrue(LearningGoalRuleSupport.isGoalFit("FOUNDATION", 3, "高级课程", List.of("计算机基础")));
        assertFalse(LearningGoalRuleSupport.isGoalFit("FOUNDATION", 3, "高级课程", List.of("后端开发")));
    }

    @Test
    void isGoalFitShouldMatchProjectAndJobByProjectKeywords() {
        assertTrue(LearningGoalRuleSupport.isGoalFit("PROJECT", 2, "Java 实战", List.of()));
        assertTrue(LearningGoalRuleSupport.isGoalFit("JOB", 2, "Java 课程", List.of("项目实战")));
        assertFalse(LearningGoalRuleSupport.isGoalFit("PROJECT", 2, "Java 课程", List.of("基础语法")));
    }

    @Test
    void isGoalFitShouldReturnFalseForBlankUnknownAndEmptyInputs() {
        assertFalse(LearningGoalRuleSupport.isGoalFit(null, 1, "基础课程", List.of("入门")));
        assertFalse(LearningGoalRuleSupport.isGoalFit(" ", 1, "基础课程", List.of("入门")));
        assertFalse(LearningGoalRuleSupport.isGoalFit("UNKNOWN", 1, "基础课程", List.of("入门")));
        assertFalse(LearningGoalRuleSupport.isGoalFit("PROJECT", 2, null, null));
    }
}
