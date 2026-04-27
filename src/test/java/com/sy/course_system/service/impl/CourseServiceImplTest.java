package com.sy.course_system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sy.course_system.dto.course.CourseRegisterOptionsDTO;
import com.sy.course_system.dto.course.CoursePageLearnerCountDTO;
import com.sy.course_system.dto.course.CoursePageTagDTO;
import com.sy.course_system.dto.course.CourseQueryDTO;
import com.sy.course_system.dto.course.CourseTempDTO;
import com.sy.course_system.dto.course.KnowledgePointOptionDTO;
import com.sy.course_system.dto.course.TagOptionDTO;
import com.sy.course_system.entity.Course;
import com.sy.course_system.entity.Knowledge;
import com.sy.course_system.common.PageResult;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.UserInfo;
import com.sy.course_system.enums.CourseOrderType;
import com.sy.course_system.enums.CourseStatus;
import com.sy.course_system.mapper.CourseHotScoreMapper;
import com.sy.course_system.mapper.CourseMapper;
import com.sy.course_system.mapper.KnowledgePointMapper;
import com.sy.course_system.mapper.TagMapper;
import com.sy.course_system.repository.CourseNodeRepository;
import com.sy.course_system.service.CourseTagService;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.service.VideoService;
import com.sy.course_system.vo.CourseVO;
import com.sy.course_system.vo.CourseUpdateVO;
import com.sy.course_system.vo.KnowledgeVO;

@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

    @Mock
    private CourseMapper courseMapper;
    @Mock
    private CourseNodeRepository courseNodeRepository;
    @Mock
    private LearningAnalysisService learningAnalysisService;
    @Mock
    private VideoService videoService;
    @Mock
    private CourseTagService courseTagService;
    @Mock
    private TagMapper tagMapper;
    @Mock
    private KnowledgePointMapper knowledgePointMapper;
    @Mock
    private CourseHotScoreMapper courseHotScoreMapper;

    @Spy
    @InjectMocks
    private CourseServiceImpl courseService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(courseService, "baseMapper", courseMapper);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void pageForUserShouldSkipEnrichmentWhenPageIsEmpty() {
        UserContext.set(new UserInfo(1L, "student", "USER"));
        CourseQueryDTO dto = new CourseQueryDTO();
        Page<CourseTempDTO> mapperPage = new Page<>(1, 9, false);
        mapperPage.setRecords(List.of());
        doReturn(mapperPage).when(courseMapper).selectCoursePage(any(), eq(1L), eq(dto));
        doReturn(0L).when(courseMapper).selectCoursePageCount(dto);

        PageResult<CourseVO> result = courseService.pageForUser(dto);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        verify(courseMapper, never()).selectCoursePageLearnerCounts(anyList());
        verify(courseMapper, never()).selectCoursePageTags(anyList());
    }

    @Test
    void pageForUserShouldEnrichCurrentPageLearnersAndTags() {
        UserContext.set(new UserInfo(1L, "student", "USER"));
        CourseQueryDTO dto = new CourseQueryDTO();
        dto.setSortBy(CourseOrderType.NEW.getCode());
        Page<CourseTempDTO> mapperPage = new Page<>(1, 9, false);
        mapperPage.setRecords(List.of(pageCourse(1L, "Java", 2), pageCourse(2L, "MySQL", 1)));
        doReturn(mapperPage).when(courseMapper).selectCoursePage(any(), eq(1L), eq(dto));
        doReturn(2L).when(courseMapper).selectCoursePageCount(dto);
        doReturn(List.of(learnerCount(1L, 3))).when(courseMapper).selectCoursePageLearnerCounts(List.of(1L, 2L));
        doReturn(List.of(pageTags(1L, "Java,后端"))).when(courseMapper).selectCoursePageTags(List.of(1L, 2L));

        PageResult<CourseVO> result = courseService.pageForUser(dto);

        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getRecords().size());
        assertEquals(3, result.getRecords().get(0).getLearners());
        assertEquals(List.of("Java", "后端"), result.getRecords().get(0).getTagList());
        assertEquals(0, result.getRecords().get(1).getLearners());
        assertNull(result.getRecords().get(1).getTagList());
    }

    @Test
    void pageForUserShouldPreserveSqlOrderForSqlSortableTypes() {
        UserContext.set(new UserInfo(1L, "student", "USER"));
        CourseQueryDTO dto = new CourseQueryDTO();
        dto.setSortBy(CourseOrderType.DEFAULT.getCode());
        Page<CourseTempDTO> mapperPage = new Page<>(1, 9, false);
        mapperPage.setRecords(List.of(pageCourse(2L, "Second", 1), pageCourse(1L, "First", 3)));
        doReturn(mapperPage).when(courseMapper).selectCoursePage(any(), eq(1L), eq(dto));
        doReturn(2L).when(courseMapper).selectCoursePageCount(dto);
        doReturn(List.of(learnerCount(1L, 9), learnerCount(2L, 1)))
                .when(courseMapper).selectCoursePageLearnerCounts(List.of(2L, 1L));
        doReturn(List.of()).when(courseMapper).selectCoursePageTags(List.of(2L, 1L));

        PageResult<CourseVO> result = courseService.pageForUser(dto);

        assertEquals(List.of(2L, 1L), result.getRecords().stream().map(CourseVO::getId).toList());
    }

    @Test
    void pageForUserShouldUseSqlHotScoreForHotSort() {
        UserContext.set(new UserInfo(1L, "student", "USER"));
        CourseQueryDTO dto = new CourseQueryDTO();
        dto.setSortBy(CourseOrderType.HOT.getCode());
        Page<CourseTempDTO> mapperPage = new Page<>(1, 9, false);
        CourseTempDTO first = pageCourse(1L, "First", 1);
        first.setHotScore(8.0);
        CourseTempDTO second = pageCourse(2L, "Second", 1);
        second.setHotScore(3.0);
        mapperPage.setRecords(List.of(first, second));
        doReturn(mapperPage).when(courseMapper).selectCoursePage(any(), eq(1L), eq(dto));
        doReturn(2L).when(courseMapper).selectCoursePageCount(dto);
        doReturn(List.of()).when(courseMapper).selectCoursePageLearnerCounts(List.of(1L, 2L));
        doReturn(List.of()).when(courseMapper).selectCoursePageTags(List.of(1L, 2L));

        PageResult<CourseVO> result = courseService.pageForUser(dto);

        assertEquals(List.of(1L, 2L), result.getRecords().stream().map(CourseVO::getId).toList());
        assertEquals(8.0, result.getRecords().get(0).getHotScore());
        assertEquals(3.0, result.getRecords().get(1).getHotScore());
        verify(learningAnalysisService, never()).sortCoursesByHotness(anyList());
    }

    @Test
    void getKnowledgePointsByCourseIdShouldMapToKnowledgeVOs() {
        Knowledge kp = new Knowledge();
        kp.setId(1L);
        kp.setName("集合");
        kp.setDifficulty(2);
        doReturn(List.of(kp)).when(courseMapper).selectKnowledgePointsByCourseId(1L);

        List<KnowledgeVO> result = courseService.getKnowledgePointsByCourseId(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("集合", result.get(0).getName());
        assertEquals(2, result.get(0).getDifficulty());
    }

    @Test
    void getKnowledgePointsByCourseIdShouldReturnNullWhenNoKnowledgePoints() {
        doReturn(List.of()).when(courseMapper).selectKnowledgePointsByCourseId(9L);

        List<KnowledgeVO> result = courseService.getKnowledgePointsByCourseId(9L);

        assertNull(result);
    }

    @Test
    void getCourseDetailForAdminShouldMapBaseFieldsAndAttachOptions() {
        Course course = new Course();
        course.setId(7L);
        course.setTitle("Java 进阶");
        course.setDescription("课程描述");
        course.setCoverUrl("cover");
        course.setDifficulty(3);
        course.setDuration(3600);

        TagOptionDTO tag = new TagOptionDTO();
        tag.setId(1);
        tag.setName("Java");
        tag.setType("TECH");
        KnowledgePointOptionDTO kp = new KnowledgePointOptionDTO();
        kp.setId(2L);
        kp.setName("集合");
        kp.setDifficulty(2);

        doReturn(course).when(courseService).getById(7L);
        doReturn(List.of(1L)).when(courseMapper).selectTagIdsByCourseId(7L);
        doReturn(List.of(tag)).when(tagMapper).listEnabledTagOptionsByIds(List.of(1L));
        doReturn(List.of(2L)).when(courseMapper).selectKnowledgePointIdsByCourseId(7L);
        doReturn(List.of(kp)).when(knowledgePointMapper).listEnabledKnowledgePointOptionsByIds(List.of(2L));
        doReturn(9).when(courseMapper).selectCategoryIdByCourseId(7L);

        CourseUpdateVO result = courseService.getCourseDetailForAdmin(7L);

        assertNotNull(result);
        assertEquals(7L, result.getId());
        assertEquals("Java 进阶", result.getTitle());
        assertEquals("课程描述", result.getDescription());
        assertEquals("cover", result.getCoverUrl());
        assertEquals(3, result.getDifficulty());
        assertEquals(3600, result.getDuration());
        assertEquals(9, result.getCategoryId());
        CourseRegisterOptionsDTO options = result.getOptions();
        assertNotNull(options);
        assertEquals(1, options.getTags().size());
        assertEquals("Java", options.getTags().get(0).getName());
        assertEquals(1, options.getKnowledgePoints().size());
        assertEquals("集合", options.getKnowledgePoints().get(0).getName());
    }

    @Test
    void updateCourseStatusShouldRemoveHotWhenOffline() {
        Course course = course(1L, CourseStatus.ONLINE.getCode());
        doReturn(course).when(courseService).getById(1L);
        doReturn(true).when(courseService).updateById(any(Course.class));

        boolean updated = courseService.updateCourseStatus(1L, CourseStatus.OFFLINE.getCode());

        assertTrue(updated);
        verify(learningAnalysisService).removeCourseHot(1L);
        verify(courseHotScoreMapper).deleteByCourseId(1L);
    }

    @Test
    void updateCourseStatusShouldRemoveHotWhenDraft() {
        Course course = course(2L, CourseStatus.ONLINE.getCode());
        doReturn(course).when(courseService).getById(2L);
        doReturn(true).when(courseService).updateById(any(Course.class));

        boolean updated = courseService.updateCourseStatus(2L, CourseStatus.DRAFT.getCode());

        assertTrue(updated);
        verify(learningAnalysisService).removeCourseHot(2L);
        verify(courseHotScoreMapper).deleteByCourseId(2L);
    }

    @Test
    void updateCourseStatusShouldReturnSuccessWhenHotCleanupFailsForOffline() {
        Course course = course(5L, CourseStatus.ONLINE.getCode());
        doReturn(course).when(courseService).getById(5L);
        doReturn(true).when(courseService).updateById(any(Course.class));
        doThrow(new RuntimeException("redis unavailable")).when(learningAnalysisService).removeCourseHot(5L);

        boolean updated = courseService.updateCourseStatus(5L, CourseStatus.OFFLINE.getCode());

        assertTrue(updated);
        verify(learningAnalysisService).removeCourseHot(5L);
        verify(courseHotScoreMapper).deleteByCourseId(5L);
    }

    @Test
    void updateCourseStatusShouldReturnSuccessWhenHotCleanupFailsForDraft() {
        Course course = course(6L, CourseStatus.ONLINE.getCode());
        doReturn(course).when(courseService).getById(6L);
        doReturn(true).when(courseService).updateById(any(Course.class));
        doThrow(new RuntimeException("redis unavailable")).when(learningAnalysisService).removeCourseHot(6L);

        boolean updated = courseService.updateCourseStatus(6L, CourseStatus.DRAFT.getCode());

        assertTrue(updated);
        verify(learningAnalysisService).removeCourseHot(6L);
        verify(courseHotScoreMapper).deleteByCourseId(6L);
    }

    @Test
    void updateCourseStatusShouldNotRemoveHotWhenOnline() {
        Course course = course(3L, CourseStatus.OFFLINE.getCode());
        doReturn(course).when(courseService).getById(3L);
        doReturn(true).when(courseService).updateById(any(Course.class));

        boolean updated = courseService.updateCourseStatus(3L, CourseStatus.ONLINE.getCode());

        assertTrue(updated);
        assertNotNull(course.getPublishTime());
        verify(learningAnalysisService, never()).removeCourseHot(3L);
        verify(courseHotScoreMapper, never()).deleteByCourseId(3L);
    }

    @Test
    void updateCourseStatusShouldNotRemoveHotWhenUpdateFails() {
        Course course = course(4L, CourseStatus.ONLINE.getCode());
        doReturn(course).when(courseService).getById(4L);
        doReturn(false).when(courseService).updateById(any(Course.class));

        boolean updated = courseService.updateCourseStatus(4L, CourseStatus.OFFLINE.getCode());

        assertFalse(updated);
        verify(learningAnalysisService, never()).removeCourseHot(4L);
        verify(courseHotScoreMapper, never()).deleteByCourseId(4L);
    }

    @Test
    void removeCoursesShouldRemoveHotBatchAfterSuccessfulDelete() {
        List<Long> courseIds = List.of(11L, 12L);
        doReturn(List.of(course(11L, CourseStatus.ONLINE.getCode()), course(12L, CourseStatus.OFFLINE.getCode())))
                .when(courseService).listByIds(courseIds);
        doReturn(true).when(courseService).removeByIds(courseIds);

        int deleted = courseService.removeCourses(courseIds);

        assertEquals(2, deleted);
        verify(courseMapper).deleteCourseCategoryRelationsByCourseIds(courseIds);
        verify(courseMapper).deleteCourseTagRelationsByCourseIds(courseIds);
        verify(courseMapper).deleteCourseKnowledgePointRelationsByCourseIds(courseIds);
        verify(courseNodeRepository).deleteCourseGraphs(courseIds);
        verify(learningAnalysisService).removeCourseHotBatch(courseIds);
        verify(courseHotScoreMapper).deleteByCourseIds(courseIds);
    }

    @Test
    void removeCoursesShouldNotRemoveHotBatchWhenDeleteFails() {
        List<Long> courseIds = List.of(21L, 22L);
        doReturn(List.of(course(21L, CourseStatus.ONLINE.getCode()))).when(courseService).listByIds(courseIds);
        doReturn(false).when(courseService).removeByIds(courseIds);

        int deleted = courseService.removeCourses(courseIds);

        assertEquals(0, deleted);
        verify(learningAnalysisService, never()).removeCourseHotBatch(anyList());
        verify(courseHotScoreMapper, never()).deleteByCourseIds(anyList());
    }

    @Test
    void removeCoursesShouldReturnSuccessWhenHotCleanupFails() {
        List<Long> courseIds = List.of(31L, 32L);
        doReturn(List.of(course(31L, CourseStatus.ONLINE.getCode()), course(32L, CourseStatus.OFFLINE.getCode())))
                .when(courseService).listByIds(courseIds);
        doReturn(true).when(courseService).removeByIds(courseIds);
        doThrow(new RuntimeException("redis unavailable")).when(learningAnalysisService).removeCourseHotBatch(courseIds);

        int deleted = courseService.removeCourses(courseIds);

        assertEquals(2, deleted);
        verify(courseMapper).deleteCourseCategoryRelationsByCourseIds(courseIds);
        verify(courseMapper).deleteCourseTagRelationsByCourseIds(courseIds);
        verify(courseMapper).deleteCourseKnowledgePointRelationsByCourseIds(courseIds);
        verify(courseNodeRepository).deleteCourseGraphs(courseIds);
        verify(learningAnalysisService).removeCourseHotBatch(courseIds);
        verify(courseHotScoreMapper).deleteByCourseIds(courseIds);
    }

    private Course course(Long id, Integer status) {
        Course course = new Course();
        course.setId(id);
        course.setStatus(status);
        return course;
    }

    private CourseTempDTO pageCourse(Long id, String title, Integer difficulty) {
        CourseTempDTO course = new CourseTempDTO();
        course.setId(id);
        course.setTitle(title);
        course.setCategory("后端");
        course.setDescription("desc");
        course.setCover("cover");
        course.setDifficulty(difficulty);
        course.setEnrolled(false);
        course.setProgress(0);
        course.setLastTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        course.setStatus(CourseStatus.ONLINE.getCode());
        return course;
    }

    private CoursePageLearnerCountDTO learnerCount(Long courseId, Integer learners) {
        CoursePageLearnerCountDTO dto = new CoursePageLearnerCountDTO();
        dto.setCourseId(courseId);
        dto.setLearners(learners);
        return dto;
    }

    private CoursePageTagDTO pageTags(Long courseId, String tags) {
        CoursePageTagDTO dto = new CoursePageTagDTO();
        dto.setCourseId(courseId);
        dto.setTags(tags);
        return dto;
    }
}
