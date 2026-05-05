package com.sy.course_system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.sy.course_system.dto.recommend.CourseReadinessDTO;
import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.dto.recommend.NewCourseBaseCandidateDTO;
import com.sy.course_system.dto.recommend.NewCourseStatDTO;
import com.sy.course_system.dto.recommend.NewCourseTagRowDTO;
import com.sy.course_system.entity.UserOnboardingProfile;
import com.sy.course_system.mapper.CourseMapper;
import com.sy.course_system.mapper.UserInterestTagMapper;
import com.sy.course_system.mapper.UserOnboardingProfileMapper;
import com.sy.course_system.repository.CourseGraphRepository;
import com.sy.course_system.vo.KnowledgeMasteryVO;

@ExtendWith(MockitoExtension.class)
class NewCourseRecommendServiceImplTest {

    @Mock
    private CourseMapper courseMapper;
    @Mock
    private UserInterestTagMapper userInterestTagMapper;
    @Mock
    private UserOnboardingProfileMapper userOnboardingProfileMapper;
    @Mock
    private CourseGraphRepository courseGraphRepository;

    @InjectMocks
    private NewCourseRecommendServiceImpl newCourseRecommendService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(newCourseRecommendService, "windowDays", 14);
        ReflectionTestUtils.setField(newCourseRecommendService, "maxLearners", 20);
        ReflectionTestUtils.setField(newCourseRecommendService, "minTagCount", 1);
        ReflectionTestUtils.setField(newCourseRecommendService, "minKpCount", 1);
        ReflectionTestUtils.setField(newCourseRecommendService, "minDurationSeconds", 300);
        ReflectionTestUtils.setField(newCourseRecommendService, "candidateLimit", 10);
        ReflectionTestUtils.setField(newCourseRecommendService, "tagWeight", 0.45d);
        ReflectionTestUtils.setField(newCourseRecommendService, "freshnessWeight", 0.30d);
        ReflectionTestUtils.setField(newCourseRecommendService, "qualityWeight", 0.20d);
        ReflectionTestUtils.setField(newCourseRecommendService, "readinessWeight", 0.05d);
        ReflectionTestUtils.setField(newCourseRecommendService, "readinessThreshold", 0.7d);
    }

    @Test
    void recommendForRegularUserShouldThrowWhenUserIdIsNull() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> newCourseRecommendService.recommendForRegularUser(null, 5));

        assertEquals("userId 不能为空", ex.getMessage());
    }

    @Test
    void recommendForRegularUserShouldReturnEmptyWhenBaseCandidatesAreEmpty() {
        when(courseMapper.selectOnlineNewCourseBaseCandidates(any(LocalDateTime.class), anyInt())).thenReturn(List.of());

        List<HybridRecommendItemDTO> result = newCourseRecommendService.recommendForRegularUser(1L, 5);

        assertTrue(result.isEmpty());
    }

    @Test
    void recommendForRegularUserShouldReturnEmptyWhenCourseIdsAreMissing() {
        NewCourseBaseCandidateDTO broken = new NewCourseBaseCandidateDTO();
        broken.setCourseId(null);
        when(courseMapper.selectOnlineNewCourseBaseCandidates(any(LocalDateTime.class), anyInt())).thenReturn(List.of(broken));

        List<HybridRecommendItemDTO> result = newCourseRecommendService.recommendForRegularUser(1L, 5);

        assertTrue(result.isEmpty());
    }

    @Test
    void recommendForRegularUserShouldFilterLowQualityCoursesBuildReasonAndFallbackReadiness() {
        LocalDateTime now = LocalDateTime.now();
        when(courseMapper.selectOnlineNewCourseBaseCandidates(any(LocalDateTime.class), eq(10))).thenReturn(List.of(
                baseCourse(101L, "Java 新课 A", 1, 1800, now.minusDays(1)),
                baseCourse(102L, "Java 新课 B", 1, 1800, now.minusDays(1)),
                baseCourse(103L, "低质量课程", 1, 100, now.minusHours(2)),
                baseCourse(104L, "老课回退", 2, 1800, null)));
        when(courseMapper.selectCourseTagRowsByCourseIds(anyList())).thenReturn(List.of(
                tagRow(101L, 1L, "Java"),
                tagRow(102L, 1L, "Java"),
                tagRow(103L, 1L, "Java"),
                tagRow(104L, 99L, "冷门")));
        when(courseMapper.selectCourseKpCountsByCourseIds(anyList())).thenReturn(List.of(
                stat(101L, 4), stat(102L, 4), stat(103L, 4), stat(104L, 4)));
        when(courseMapper.selectCourseLearnerCountsByCourseIds(anyList())).thenReturn(List.of(
                stat(101L, 10), stat(102L, 10), stat(103L, 10), stat(104L, 10)));
        when(userInterestTagMapper.selectTagIdsByUserIdAndSource(1L, "INIT")).thenReturn(List.of(1L));
        // 103 被质量门槛过滤后，不应再进入 Neo4j readiness 批量查询。
        when(courseGraphRepository.getCourseReadinessBatch(eq(1L), eq(List.of(101L, 102L, 104L)), eq(0.7d))).thenReturn(List.of(
                readiness(101L, 0.8d),
                readiness(102L, 0.8d)));

        List<HybridRecommendItemDTO> result = newCourseRecommendService.recommendForRegularUser(1L, 3);

        assertEquals(List.of(101L, 102L, 104L), result.stream().map(HybridRecommendItemDTO::getCourseId).toList());
        assertTrue(result.get(0).getReason().contains("匹配兴趣标签：Java"));
        assertTrue(result.get(0).getReason().contains("内容信息较完整"));
        assertEquals(1.0d, result.get(2).getReadiness());
        assertTrue(result.get(2).getReason().contains("可学习性 1.00"));
        verify(courseMapper).selectOnlineNewCourseBaseCandidates(any(LocalDateTime.class), eq(10));
        verify(courseGraphRepository).getCourseReadinessBatch(1L, List.of(101L, 102L, 104L), 0.7d);
    }

    @Test
    void recommendForRegularUserShouldSkipUserAndGraphQueriesWhenAllCandidatesFailQualityGate() {
        when(courseMapper.selectOnlineNewCourseBaseCandidates(any(LocalDateTime.class), eq(10))).thenReturn(List.of(
                baseCourse(501L, "无标签课程", 1, 1800, LocalDateTime.now().minusDays(1)),
                baseCourse(502L, "时长不足课程", 1, 100, LocalDateTime.now().minusDays(1))));
        when(courseMapper.selectCourseTagRowsByCourseIds(anyList())).thenReturn(List.of(tagRow(502L, 1L, "Java")));
        when(courseMapper.selectCourseKpCountsByCourseIds(anyList())).thenReturn(List.of(
                stat(501L, 4),
                stat(502L, 4)));
        when(courseMapper.selectCourseLearnerCountsByCourseIds(anyList())).thenReturn(List.of(
                stat(501L, 1),
                stat(502L, 1)));

        List<HybridRecommendItemDTO> result = newCourseRecommendService.recommendForRegularUser(1L, 3);

        assertTrue(result.isEmpty());
        // 全部候选都未通过质量门槛时，后续用户画像和图谱查询都没有可消费的候选。
        verify(userInterestTagMapper, never()).selectTagIdsByUserIdAndSource(any(), any());
        verify(userOnboardingProfileMapper, never()).selectByUserId(any());
        verify(courseGraphRepository, never()).getCourseReadinessBatch(any(), anyList(), any());
    }

    @Test
    void recommendForRegularUserShouldNormalizeLimitAndUseMaxOfCandidateLimitAndSafeLimit() {
        ReflectionTestUtils.setField(newCourseRecommendService, "candidateLimit", 2);
        List<NewCourseBaseCandidateDTO> baseCourses = buildBulkBaseCourses(60, LocalDateTime.now().minusHours(1));
        when(courseMapper.selectOnlineNewCourseBaseCandidates(any(LocalDateTime.class), anyInt())).thenReturn(baseCourses);
        when(courseMapper.selectCourseTagRowsByCourseIds(anyList())).thenReturn(buildBulkTagRows(60));
        when(courseMapper.selectCourseKpCountsByCourseIds(anyList())).thenReturn(buildBulkStats(60, 4));
        when(courseMapper.selectCourseLearnerCountsByCourseIds(anyList())).thenReturn(buildBulkStats(60, 1));
        when(userInterestTagMapper.selectTagIdsByUserIdAndSource(1L, "INIT")).thenReturn(List.of());
        when(courseGraphRepository.getCourseReadinessBatch(eq(1L), anyList(), eq(0.7d))).thenReturn(List.of());

        List<HybridRecommendItemDTO> nullLimit = newCourseRecommendService.recommendForRegularUser(1L, null);
        verify(courseMapper).selectOnlineNewCourseBaseCandidates(any(LocalDateTime.class), eq(10));
        clearInvocations(courseMapper);

        List<HybridRecommendItemDTO> negativeLimit = newCourseRecommendService.recommendForRegularUser(1L, -1);
        verify(courseMapper).selectOnlineNewCourseBaseCandidates(any(LocalDateTime.class), eq(2));
        clearInvocations(courseMapper);

        List<HybridRecommendItemDTO> hugeLimit = newCourseRecommendService.recommendForRegularUser(1L, 100);
        verify(courseMapper).selectOnlineNewCourseBaseCandidates(any(LocalDateTime.class), eq(50));

        assertEquals(10, nullLimit.size());
        assertEquals(1, negativeLimit.size());
        assertEquals(50, hugeLimit.size());
    }

    @Test
    void recommendForRegularUserShouldUseFreshnessThenCourseIdForTieBreaks() {
        ReflectionTestUtils.setField(newCourseRecommendService, "tagWeight", 0.0d);
        ReflectionTestUtils.setField(newCourseRecommendService, "freshnessWeight", 1.0d);
        ReflectionTestUtils.setField(newCourseRecommendService, "qualityWeight", 0.0d);
        ReflectionTestUtils.setField(newCourseRecommendService, "readinessWeight", 0.0d);
        ReflectionTestUtils.setField(newCourseRecommendService, "candidateLimit", 10);

        LocalDateTime now = LocalDateTime.now();
        when(courseMapper.selectOnlineNewCourseBaseCandidates(any(LocalDateTime.class), eq(10))).thenReturn(List.of(
                baseCourse(2L, "课程 2", 1, 1800, now.minusHours(1)),
                baseCourse(1L, "课程 1", 1, 1800, now.minusHours(1)),
                baseCourse(3L, "课程 3", 1, 1800, now.minusDays(1))));
        when(courseMapper.selectCourseTagRowsByCourseIds(anyList())).thenReturn(List.of(
                tagRow(1L, 11L, "标签"), tagRow(2L, 12L, "标签"), tagRow(3L, 13L, "标签")));
        when(courseMapper.selectCourseKpCountsByCourseIds(anyList())).thenReturn(List.of(
                stat(1L, 4), stat(2L, 4), stat(3L, 4)));
        when(courseMapper.selectCourseLearnerCountsByCourseIds(anyList())).thenReturn(List.of(
                stat(1L, 1), stat(2L, 1), stat(3L, 1)));
        when(userInterestTagMapper.selectTagIdsByUserIdAndSource(1L, "INIT")).thenReturn(List.of());
        when(courseGraphRepository.getCourseReadinessBatch(eq(1L), anyList(), eq(0.7d))).thenReturn(List.of());

        List<HybridRecommendItemDTO> result = newCourseRecommendService.recommendForRegularUser(1L, 3);

        assertEquals(List.of(1L, 2L, 3L), result.stream().map(HybridRecommendItemDTO::getCourseId).toList());
    }

    @Test
    void recommendForRegularUserShouldApplyLearningGoalBonusAndAppendReason() {
        LocalDateTime now = LocalDateTime.now();
        when(courseMapper.selectOnlineNewCourseBaseCandidates(any(LocalDateTime.class), eq(10))).thenReturn(List.of(
                baseCourse(301L, "Java 原理", 2, 1800, now.minusDays(1)),
                baseCourse(302L, "Java 进阶", 2, 1800, now.minusDays(1))));
        when(courseMapper.selectCourseTagRowsByCourseIds(anyList())).thenReturn(List.of(
                tagRow(301L, 1L, "Java"),
                tagRow(302L, 1L, "Java")));
        when(courseMapper.selectCourseKpCountsByCourseIds(anyList())).thenReturn(List.of(
                stat(301L, 1),
                stat(302L, 2)));
        when(courseMapper.selectCourseLearnerCountsByCourseIds(anyList())).thenReturn(List.of(
                stat(301L, 1),
                stat(302L, 1)));
        when(userInterestTagMapper.selectTagIdsByUserIdAndSource(7L, "INIT")).thenReturn(List.of(1L));
        when(userOnboardingProfileMapper.selectByUserId(7L)).thenReturn(profile("FOUNDATION"));
        when(courseGraphRepository.getCourseReadinessBatch(eq(7L), anyList(), eq(0.7d))).thenReturn(List.of(
                readiness(301L, 0.8d),
                readiness(302L, 0.8d)));

        List<HybridRecommendItemDTO> result = newCourseRecommendService.recommendForRegularUser(7L, 2);

        assertEquals(List.of(301L, 302L), result.stream().map(HybridRecommendItemDTO::getCourseId).toList());
        assertTrue(result.get(0).getReason().contains("符合当前学习目标（打基础）"));
        assertFalse(result.get(1).getReason().contains("符合当前学习目标"));
    }

    @Test
    void recommendForRegularUserShouldKeepPrimarySignalsDominantWhenGoalBonusIsNotEnough() {
        LocalDateTime now = LocalDateTime.now();
        when(courseMapper.selectOnlineNewCourseBaseCandidates(any(LocalDateTime.class), eq(10))).thenReturn(List.of(
                baseCourse(401L, "Java 实战", 2, 1800, now.minusDays(1)),
                baseCourse(402L, "Java 基础", 2, 1800, now.minusHours(1))));
        when(courseMapper.selectCourseTagRowsByCourseIds(anyList())).thenReturn(List.of(
                tagRow(401L, 99L, "项目实战"),
                tagRow(402L, 1L, "Java")));
        when(courseMapper.selectCourseKpCountsByCourseIds(anyList())).thenReturn(List.of(
                stat(401L, 4),
                stat(402L, 4)));
        when(courseMapper.selectCourseLearnerCountsByCourseIds(anyList())).thenReturn(List.of(
                stat(401L, 1),
                stat(402L, 1)));
        when(userInterestTagMapper.selectTagIdsByUserIdAndSource(8L, "INIT")).thenReturn(List.of(1L));
        when(userOnboardingProfileMapper.selectByUserId(8L)).thenReturn(profile("PROJECT"));
        when(courseGraphRepository.getCourseReadinessBatch(eq(8L), anyList(), eq(0.7d))).thenReturn(List.of(
                readiness(401L, 0.8d),
                readiness(402L, 0.8d)));

        List<HybridRecommendItemDTO> result = newCourseRecommendService.recommendForRegularUser(8L, 2);

        assertEquals(List.of(402L, 401L), result.stream().map(HybridRecommendItemDTO::getCourseId).toList());
        assertFalse(result.get(0).getReason().contains("符合当前学习目标"));
        assertTrue(result.get(1).getReason().contains("符合当前学习目标（做项目）"));
    }

    @Test
    void recommendForRegularUserShouldReturnZeroWhenAllWeightsAreNonPositive() {
        ReflectionTestUtils.setField(newCourseRecommendService, "tagWeight", -1.0d);
        ReflectionTestUtils.setField(newCourseRecommendService, "freshnessWeight", -1.0d);
        ReflectionTestUtils.setField(newCourseRecommendService, "qualityWeight", -1.0d);
        ReflectionTestUtils.setField(newCourseRecommendService, "readinessWeight", -1.0d);

        when(courseMapper.selectOnlineNewCourseBaseCandidates(any(LocalDateTime.class), eq(10))).thenReturn(List.of(
                baseCourse(201L, "零权重新课", 1, 1800, LocalDateTime.now().minusDays(1))));
        when(courseMapper.selectCourseTagRowsByCourseIds(anyList())).thenReturn(List.of(tagRow(201L, 1L, "Java")));
        when(courseMapper.selectCourseKpCountsByCourseIds(anyList())).thenReturn(List.of(stat(201L, 4)));
        when(courseMapper.selectCourseLearnerCountsByCourseIds(anyList())).thenReturn(List.of(stat(201L, 1)));
        when(userInterestTagMapper.selectTagIdsByUserIdAndSource(1L, "INIT")).thenReturn(List.of(1L));
        when(courseGraphRepository.getCourseReadinessBatch(eq(1L), anyList(), eq(0.7d))).thenReturn(List.of(
                readiness(201L, 0.6d)));

        List<HybridRecommendItemDTO> result = newCourseRecommendService.recommendForRegularUser(1L, 1);

        assertEquals(0.0d, result.get(0).getFinalScore());
    }

    private UserOnboardingProfile profile(String learningGoal) {
        UserOnboardingProfile profile = new UserOnboardingProfile();
        profile.setLearningGoal(learningGoal);
        return profile;
    }

    private NewCourseBaseCandidateDTO baseCourse(Long courseId, String title, Integer difficulty, Integer duration,
            LocalDateTime publishTime) {
        NewCourseBaseCandidateDTO dto = new NewCourseBaseCandidateDTO();
        dto.setCourseId(courseId);
        dto.setTitle(title);
        dto.setCoverUrl("cover-" + courseId);
        dto.setDifficulty(difficulty);
        dto.setDuration(duration);
        dto.setPublishTime(publishTime);
        return dto;
    }

    private NewCourseTagRowDTO tagRow(Long courseId, Long tagId, String tagName) {
        NewCourseTagRowDTO row = new NewCourseTagRowDTO();
        row.setCourseId(courseId);
        row.setTagId(tagId);
        row.setTagName(tagName);
        return row;
    }

    private NewCourseStatDTO stat(Long courseId, Integer count) {
        NewCourseStatDTO dto = new NewCourseStatDTO();
        dto.setCourseId(courseId);
        dto.setCountValue(count);
        return dto;
    }

    private CourseReadinessDTO readiness(Long courseId, Double readiness) {
        CourseReadinessDTO dto = new CourseReadinessDTO();
        dto.setCourseId(courseId);
        dto.setReadiness(readiness);
        dto.setMissing(List.of(new KnowledgeMasteryVO(1L, "先修", 1, 0.1d, 0.7d)));
        return dto;
    }

    private List<NewCourseBaseCandidateDTO> buildBulkBaseCourses(int count, LocalDateTime publishTime) {
        List<NewCourseBaseCandidateDTO> rows = new ArrayList<>();
        IntStream.rangeClosed(1, count).forEach(i -> rows.add(baseCourse((long) i, "课程" + i, 1, 1800, publishTime)));
        return rows;
    }

    private List<NewCourseTagRowDTO> buildBulkTagRows(int count) {
        List<NewCourseTagRowDTO> rows = new ArrayList<>();
        IntStream.rangeClosed(1, count).forEach(i -> rows.add(tagRow((long) i, 1000L + i, "标签" + i)));
        return rows;
    }

    private List<NewCourseStatDTO> buildBulkStats(int count, int value) {
        List<NewCourseStatDTO> rows = new ArrayList<>();
        IntStream.rangeClosed(1, count).forEach(i -> rows.add(stat((long) i, value)));
        return rows;
    }
}
