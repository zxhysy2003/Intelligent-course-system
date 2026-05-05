package com.sy.course_system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sy.course_system.dto.coldstart.ColdStartCourseCandidateDTO;
import com.sy.course_system.entity.UserOnboardingProfile;
import com.sy.course_system.mapper.CourseMapper;
import com.sy.course_system.mapper.UserInterestTagMapper;
import com.sy.course_system.mapper.UserOnboardingProfileMapper;
import com.sy.course_system.vo.ColdStartRecommendItemVO;

@ExtendWith(MockitoExtension.class)
class ColdStartRecommendServiceImplTest {

    @Mock
    private UserOnboardingProfileMapper userOnboardingProfileMapper;
    @Mock
    private UserInterestTagMapper userInterestTagMapper;
    @Mock
    private CourseMapper courseMapper;

    @InjectMocks
    private ColdStartRecommendServiceImpl coldStartRecommendService;

    @Test
    void recommendShouldThrowWhenUserIdIsNull() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> coldStartRecommendService.recommend(null, 10));

        assertEquals("userId 不能为空", ex.getMessage());
    }

    @Test
    void recommendShouldReturnEmptyWhenNoCourseCandidates() {
        when(userOnboardingProfileMapper.selectByUserId(1L)).thenReturn(null);
        when(userInterestTagMapper.selectTagIdsByUserIdAndSource(1L, "INIT")).thenReturn(List.of());
        when(courseMapper.selectPublishedCoursesWithTagsForColdStart()).thenReturn(List.of());

        List<ColdStartRecommendItemVO> result = coldStartRecommendService.recommend(1L, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void recommendShouldPrioritizeMatchedCoursesAndFillFallbackWithoutDuplicates() {
        when(userOnboardingProfileMapper.selectByUserId(6L)).thenReturn(profile(2, 1, null));
        when(userInterestTagMapper.selectTagIdsByUserIdAndSource(6L, "INIT")).thenReturn(List.of(101L, 102L));
        when(courseMapper.selectPublishedCoursesWithTagsForColdStart()).thenReturn(List.of(
                row(1L, "Java 实战", 2, 101L, "Java"),
                row(2L, "Spring Boot 基础入门", 1, 101L, "Java"),
                row(2L, "Spring Boot 基础入门", 1, 102L, "Spring Boot"),
                row(3L, "计算机基础导论", 1, 201L, "计算机基础"),
                row(4L, "数据库原理", 3, 301L, "MySQL")));

        List<ColdStartRecommendItemVO> result = coldStartRecommendService.recommend(6L, 3);

        assertEquals(List.of(2L, 1L, 3L), result.stream().map(ColdStartRecommendItemVO::getCourseId).toList());
        assertEquals(3, new LinkedHashSet<>(result.stream().map(ColdStartRecommendItemVO::getCourseId).toList()).size());
        assertTrue(result.get(0).getReason().contains("匹配兴趣标签：Java、Spring Boot"));
        assertTrue(result.get(0).getReason().contains("难度适合当前水平"));
        assertTrue(result.get(2).getReason().contains("默认基础课程兜底"));
    }

    @Test
    void recommendShouldUseFallbackWhenOnboardingIncompleteAndKeepStableSortOnTie() {
        when(userOnboardingProfileMapper.selectByUserId(8L)).thenReturn(profile(1, 0, null));
        when(userInterestTagMapper.selectTagIdsByUserIdAndSource(8L, "INIT")).thenReturn(List.of());
        when(courseMapper.selectPublishedCoursesWithTagsForColdStart()).thenReturn(List.of(
                row(9L, "数据库基础", 1, 901L, "后端开发"),
                row(8L, "计算机基础", 1, 902L, "计算机基础"),
                row(10L, "算法导论", 1, 903L, "算法")));

        List<ColdStartRecommendItemVO> result = coldStartRecommendService.recommend(8L, 2);

        assertEquals(List.of(8L, 9L), result.stream().map(ColdStartRecommendItemVO::getCourseId).toList());
        assertTrue(result.stream().allMatch(item -> item.getReason().contains("默认基础课程兜底")));
    }

    @Test
    void recommendShouldNormalizeLimitForNullNonPositiveAndTooLargeValues() {
        when(userOnboardingProfileMapper.selectByUserId(9L)).thenReturn(null);
        when(userInterestTagMapper.selectTagIdsByUserIdAndSource(9L, "INIT")).thenReturn(List.of());
        when(courseMapper.selectPublishedCoursesWithTagsForColdStart()).thenReturn(buildBulkFallbackRows(60));

        List<ColdStartRecommendItemVO> nullLimitResult = coldStartRecommendService.recommend(9L, null);
        List<ColdStartRecommendItemVO> zeroLimitResult = coldStartRecommendService.recommend(9L, 0);
        List<ColdStartRecommendItemVO> hugeLimitResult = coldStartRecommendService.recommend(9L, 1000);

        assertEquals(10, nullLimitResult.size());
        assertEquals(10, zeroLimitResult.size());
        assertEquals(50, hugeLimitResult.size());
    }

    @Test
    void recommendShouldApplyFoundationGoalBonusAndAppendReason() {
        when(userOnboardingProfileMapper.selectByUserId(10L)).thenReturn(profile(2, 1, "FOUNDATION"));
        when(userInterestTagMapper.selectTagIdsByUserIdAndSource(10L, "INIT")).thenReturn(List.of(100L));
        when(courseMapper.selectPublishedCoursesWithTagsForColdStart()).thenReturn(List.of(
                row(1L, "Java 原理", 2, 100L, "Java"),
                row(2L, "Java 入门", 2, 100L, "Java")));

        List<ColdStartRecommendItemVO> result = coldStartRecommendService.recommend(10L, 2);

        assertEquals(List.of(2L, 1L), result.stream().map(ColdStartRecommendItemVO::getCourseId).toList());
        assertTrue(result.get(0).getReason().contains("符合当前学习目标（打基础）"));
    }

    @Test
    void recommendShouldApplyProjectGoalBonusAndAppendReason() {
        when(userOnboardingProfileMapper.selectByUserId(11L)).thenReturn(profile(2, 1, "PROJECT"));
        when(userInterestTagMapper.selectTagIdsByUserIdAndSource(11L, "INIT")).thenReturn(List.of(100L));
        when(courseMapper.selectPublishedCoursesWithTagsForColdStart()).thenReturn(List.of(
                row(1L, "Java 实战", 2, 100L, "Java"),
                row(2L, "Java 基础", 2, 100L, "Java")));

        List<ColdStartRecommendItemVO> result = coldStartRecommendService.recommend(11L, 2);

        assertEquals(List.of(1L, 2L), result.stream().map(ColdStartRecommendItemVO::getCourseId).toList());
        assertTrue(result.get(0).getReason().contains("符合当前学习目标（做项目）"));
    }

    private UserOnboardingProfile profile(Integer level, Integer status, String learningGoal) {
        UserOnboardingProfile profile = new UserOnboardingProfile();
        profile.setCurrentLevel(level);
        profile.setOnboardingStatus(status);
        profile.setLearningGoal(learningGoal);
        return profile;
    }

    private ColdStartCourseCandidateDTO row(Long courseId, String title, Integer difficulty, Long tagId, String tagName) {
        ColdStartCourseCandidateDTO dto = new ColdStartCourseCandidateDTO();
        dto.setCourseId(courseId);
        dto.setTitle(title);
        dto.setCoverUrl("cover-" + courseId);
        dto.setDifficulty(difficulty);
        dto.setTagId(tagId);
        dto.setTagName(tagName);
        return dto;
    }

    private List<ColdStartCourseCandidateDTO> buildBulkFallbackRows(int count) {
        List<ColdStartCourseCandidateDTO> rows = new ArrayList<>();
        IntStream.rangeClosed(1, count).forEach(i -> rows.add(row((long) i, "入门课程" + i, 1, 1000L + i, "其他标签")));
        return rows;
    }
}
