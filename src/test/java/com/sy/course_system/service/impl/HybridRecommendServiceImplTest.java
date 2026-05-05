package com.sy.course_system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.dto.graph.CourseKnowledgePointDTO;
import com.sy.course_system.dto.recommend.CourseReadinessDTO;
import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;
import com.sy.course_system.dto.recommend.RecommendItemDTO;
import com.sy.course_system.dto.recommend.RecommendResponseDTO;
import com.sy.course_system.entity.Course;
import com.sy.course_system.repository.CourseGraphRepository;
import com.sy.course_system.service.ColdStartRecommendService;
import com.sy.course_system.service.ColdStartSupportService;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.service.NewCourseRecommendService;
import com.sy.course_system.service.RecommendService;
import com.sy.course_system.vo.ColdStartRecommendItemVO;
import com.sy.course_system.vo.KnowledgeMasteryVO;

@ExtendWith(MockitoExtension.class)
class HybridRecommendServiceImplTest {

    @Mock
    private RecommendService recommendService;
    @Mock
    private CourseGraphRepository courseGraphRepository;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Neo4jClient neo4jClient;
    @Mock
    private CourseService courseService;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOperations;
    @Mock
    private ColdStartSupportService coldStartSupportService;
    @Mock
    private ColdStartRecommendService coldStartRecommendService;
    @Mock
    private NewCourseRecommendService newCourseRecommendService;
    @Mock
    private LearningAnalysisService learningAnalysisService;
    @Mock
    private Executor recommendTaskExecutor;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private HybridRecommendServiceImpl hybridRecommendService;

    @BeforeEach
    void setUp() {
        // 测试统一打开新课推荐，并把注入限制调成"容易观察"的配置，
        // 这样不同用例都能稳定验证"新课注入是否影响最终顺序与展示分"。
        ReflectionTestUtils.setField(hybridRecommendService, "newCourseRecommendEnabled", true);
        ReflectionTestUtils.setField(hybridRecommendService, "newCourseInjectLimit", 2);
        ReflectionTestUtils.setField(hybridRecommendService, "newCourseMaxExposureRatio", 1.0d);
        // 默认走串行路径，让现有测试断言不受异步执行调度影响；
        // 异步分支由专用测试用例在启用 asyncEnabled 后单独验证。
        ReflectionTestUtils.setField(hybridRecommendService, "asyncEnabled", false);

        // 缓存与 Neo4j 查询不是本测试关注点时，统一给出宽松默认桩，
        // 每个用例只覆盖自己真正关心的分支，避免样板 stub 淹没断言重点。
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(neo4jClient.query(anyString()).bindAll(anyMap()).fetch().all()).thenReturn(List.of());
        lenient().when(courseGraphRepository.findCourseKnowledgePointsBatch(anyList())).thenReturn(List.of());

        // 异步测试中 executor 直接在当前线程执行，既模拟并行语义又保证断言确定性。
        lenient().doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(recommendTaskExecutor).execute(any(Runnable.class));
    }

    @Test
    void recommendShouldReturnCachedRegularResponseWithoutCallingBuilderDependencies() {
        // 这里故意直接把"未带 recommendScore 的 DTO 对象"塞进缓存，
        // 用来验证 readCache 的自修复逻辑：
        // 即使缓存命中的对象是旧版本结构，读出时也会即时补齐展示分，而不需要手工清缓存。
        HybridRecommendResponseDTO cached = new HybridRecommendResponseDTO(1L, List.of(hybridItem(10L, "缓存课程")));
        when(coldStartSupportService.isColdStartUser(1L)).thenReturn(false);
        when(valueOperations.get("recommend:user:1")).thenReturn(cached);

        HybridRecommendResponseDTO result = hybridRecommendService.recommend(1L);

        assertSame(cached, result);
        // hybridItem 默认 finalScore=0.9，按 clamp01 后映射为 60 + 0.9 * 35 = 91.5，四舍五入得 92。
        assertEquals(92, result.getItems().get(0).getRecommendScore());
        verify(recommendService, never()).recommend(any());
        verify(newCourseRecommendService, never()).recommendForRegularUser(any(), any());
    }

    @Test
    void recommendShouldBuildColdStartResponseAndKeepGraphFieldsNullSafe() {
        // 第一条是正常冷启动课程，第二条故意构造 courseId=null 的脏数据，
        // 验证冷启动链路在补图谱解释字段时仍能保持空安全。
        ColdStartRecommendItemVO valid = coldStartItem(100L, "冷启动课程", 1, 0.9d, "匹配兴趣标签：Java");
        ColdStartRecommendItemVO broken = coldStartItem(null, "异常课程", 1, 0.2d, "兜底");

        when(coldStartSupportService.isColdStartUser(1L)).thenReturn(true);
        when(valueOperations.get("recommend:cold:user:1")).thenReturn((Object) null, (Object) null);
        when(valueOperations.setIfAbsent("recommend:cold:lock:user:1", "1", 20L, TimeUnit.SECONDS)).thenReturn(true);
        when(coldStartRecommendService.recommend(1L, 10)).thenReturn(List.of(valid, broken));
        when(courseGraphRepository.getCourseReadinessBatch(1L, List.of(100L), 0.7d)).thenReturn(List.of(
                readiness(100L, 0.6d)));

        HybridRecommendResponseDTO result = hybridRecommendService.recommend(1L);

        assertEquals(2, result.getItems().size());
        HybridRecommendItemDTO first = result.getItems().get(0);
        HybridRecommendItemDTO second = result.getItems().get(1);
        assertEquals("COLD_START_USER", first.getRecommendSource());
        assertEquals(Boolean.FALSE, first.getIsNewCourse());
        assertNotNull(first.getKnowledgePoints());
        assertNotNull(first.getLearningPaths());
        assertEquals(1, first.getMissingPrerequisitesMastery().size());
        // 冷启动展示分使用指数压缩：
        // 0.9 -> 1 - exp(-0.9 / 10) ≈ 0.0861 -> 63
        assertEquals(63, first.getRecommendScore());
        // 0.2 -> 1 - exp(-0.2 / 10) ≈ 0.0198 -> 61
        assertEquals(61, second.getRecommendScore());
        assertTrue(first.getRecommendScore() > second.getRecommendScore());
        assertTrue(second.getKnowledgePoints().isEmpty());
        assertTrue(second.getMissingPrerequisitesMastery().isEmpty());
        assertTrue(second.getLearningPaths().isEmpty());
        verify(valueOperations).set(eq("recommend:cold:user:1"), any(HybridRecommendResponseDTO.class), eq(10L),
                eq(TimeUnit.MINUTES));
    }

    @Test
    void recommendShouldUseNewCourseFallbackWhenCfResponseIsEmpty() {
        // 该用例覆盖"CF 完全空，但新课候选可用"的兜底分支。
        // 此时不重写新课原有 finalScore，只验证最终出参会统一补 recommendScore。
        HybridRecommendItemDTO newCourse = hybridItem(5L, "新课兜底");
        newCourse.setRecommendSource("COLD_START_COURSE");
        newCourse.setIsNewCourse(Boolean.TRUE);
        newCourse.setReadiness(null);

        when(coldStartSupportService.isColdStartUser(1L)).thenReturn(false);
        when(valueOperations.get("recommend:user:1")).thenReturn((Object) null, (Object) null);
        when(valueOperations.setIfAbsent("recommend:lock:user:1", "1", 20L, TimeUnit.SECONDS)).thenReturn(true);
        when(recommendService.recommend(1L)).thenReturn(recommendResponseDto(List.of()));
        when(newCourseRecommendService.recommendForRegularUser(1L, 30)).thenReturn(List.of(newCourse));
        when(courseGraphRepository.getCourseReadinessBatch(1L, List.of(5L), 0.7d)).thenReturn(List.of(readiness(5L, 0.6d)));

        HybridRecommendResponseDTO result = hybridRecommendService.recommend(1L);

        assertEquals(1, result.getItems().size());
        HybridRecommendItemDTO item = result.getItems().get(0);
        assertEquals(5L, item.getCourseId());
        assertEquals("COLD_START_COURSE", item.getRecommendSource());
        assertEquals(Boolean.TRUE, item.getIsNewCourse());
        assertEquals(0.6d, item.getReadiness());
        // 新课冷启动当前直接沿用 finalScore=0.9 做 clamp01，再统一映射到展示分。
        assertEquals(92, item.getRecommendScore());
        assertNotNull(item.getKnowledgePoints());
        assertNotNull(item.getLearningPaths());
    }

    @Test
    void recommendShouldMergeCfAndNewCourseAndFetchMissingReadiness() {
        // 该用例同时验证三件事：
        // 1) CF 主链路仍按 finalScore 排序；
        // 2) 新课只按既定插槽注入，不改原排序语义；
        // 3) 新注入课程如果不在初始 readinessMap 中，会补查图谱数据。
        HybridRecommendItemDTO newCourse = hybridItem(4L, "新课注入");
        newCourse.setRecommendSource("COLD_START_COURSE");
        newCourse.setIsNewCourse(Boolean.TRUE);
        HybridRecommendItemDTO duplicate = hybridItem(2L, "重复新课");
        duplicate.setRecommendSource("COLD_START_COURSE");
        duplicate.setIsNewCourse(Boolean.TRUE);

        when(coldStartSupportService.isColdStartUser(1L)).thenReturn(false);
        when(valueOperations.get("recommend:user:1")).thenReturn((Object) null, (Object) null);
        when(valueOperations.setIfAbsent("recommend:lock:user:1", "1", 20L, TimeUnit.SECONDS)).thenReturn(true);
        when(recommendService.recommend(1L)).thenReturn(recommendResponseDto(List.of(
                cfItem(1L, 10.0d),
                cfItem(2L, 8.0d),
                cfItem(3L, 6.0d))));
        when(newCourseRecommendService.recommendForRegularUser(1L, 30)).thenReturn(List.of(newCourse, duplicate));
        when(courseService.getRecommendCourseSummaryMapByIds(List.of(1L, 2L, 3L))).thenReturn(Map.of(
                1L, courseSummary(1L, "CF 课程 1", "cover-1", 1),
                2L, courseSummary(2L, "CF 课程 2", "cover-2", 2),
                3L, courseSummary(3L, "CF 课程 3", "cover-3", 3)));
        when(courseGraphRepository.getCourseReadinessBatch(eq(1L), anyList(), eq(0.7d))).thenAnswer(invocation -> {
            List<Long> courseIds = invocation.getArgument(1);
            if (courseIds.equals(List.of(1L, 2L, 3L))) {
                return java.util.Arrays.asList(
                        readiness(1L, 0.2d),
                        readiness(2L, 1.0d),
                        readiness(2L, 0.4d),
                        null,
                        readiness(3L, 0.8d));
            }
            if (courseIds.equals(List.of(4L))) {
                return List.of(readiness(4L, 0.6d));
            }
            return List.of();
        });

        HybridRecommendResponseDTO result = hybridRecommendService.recommend(1L);

        assertEquals(List.of(1L, 2L, 4L, 3L), result.getItems().stream().map(HybridRecommendItemDTO::getCourseId).toList());
        assertEquals(4, result.getItems().stream().map(HybridRecommendItemDTO::getCourseId).distinct().count());
        assertEquals(0.76d, result.getItems().get(0).getFinalScore(), 1e-9);
        // 这里的 87/83/92/68 对应的是"不同来源 finalScore 统一换算后的展示分"，
        // 不是重新参与排序的分值，主要用来保证前端拿到可直接展示的统一口径。
        assertEquals(List.of(87, 83, 92, 68),
                result.getItems().stream().map(HybridRecommendItemDTO::getRecommendScore).toList());
        assertEquals("cover-1", result.getItems().get(0).getCoverUrl());
        assertEquals(1, result.getItems().get(0).getDifficulty());
        assertEquals("根据你的学习行为推荐；建议先补齐先修知识", result.getItems().get(0).getReason());
        assertEquals("根据你的学习行为推荐；当前可直接学习", result.getItems().get(1).getReason());
        assertEquals(0.6d, result.getItems().get(2).getReadiness());
        verify(courseGraphRepository).getCourseReadinessBatch(1L, List.of(4L), 0.7d);
        verify(valueOperations).set(eq("recommend:user:1"), any(HybridRecommendResponseDTO.class), eq(30L),
                eq(TimeUnit.MINUTES));
    }

    @Test
    void recommendShouldUseGenericCfReasonWhenReadinessIsMissing() {
        // 这里验证"排序兜底值"和"解释文案"故意分离：
        // readiness 缺失时，排序上允许按 1.0 兜底，避免无图谱课程被系统性压低；
        // 但文案上不能误写成"当前可直接学习"，因此 reason 仍应保持通用描述。
        when(coldStartSupportService.isColdStartUser(3L)).thenReturn(false);
        when(valueOperations.get("recommend:user:3")).thenReturn((Object) null, (Object) null);
        when(valueOperations.setIfAbsent("recommend:lock:user:3", "1", 20L, TimeUnit.SECONDS)).thenReturn(true);
        when(recommendService.recommend(3L)).thenReturn(recommendResponseDto(List.of(cfItem(30L, 9.0d))));
        when(newCourseRecommendService.recommendForRegularUser(3L, 30)).thenReturn(List.of());
        when(courseService.getRecommendCourseSummaryMapByIds(List.of(30L))).thenReturn(Map.of(
                30L, courseSummary(30L, "无图谱课程", "cover-30", 2)));
        when(courseGraphRepository.getCourseReadinessBatch(3L, List.of(30L), 0.7d)).thenReturn(List.of());

        HybridRecommendResponseDTO result = hybridRecommendService.recommend(3L);

        assertEquals(1, result.getItems().size());
        HybridRecommendItemDTO item = result.getItems().get(0);
        assertEquals(1.0d, item.getReadiness());
        assertEquals("根据你的学习行为推荐", item.getReason());
        assertEquals(71, item.getRecommendScore());
    }

    @Test
    void recommendShouldBatchLoadKnowledgePointsAndKeepMissingCoursesEmpty() {
        ColdStartRecommendItemVO first = coldStartItem(100L, "课程 A", 1, 0.9d, "匹配兴趣标签：Java");
        ColdStartRecommendItemVO second = coldStartItem(200L, "课程 B", 1, 0.8d, "兜底");
        CourseKnowledgePointDTO kp = knowledgePointRow(100L, 11L, "集合", 2);

        when(coldStartSupportService.isColdStartUser(6L)).thenReturn(true);
        when(valueOperations.get("recommend:cold:user:6")).thenReturn((Object) null, (Object) null);
        when(valueOperations.setIfAbsent("recommend:cold:lock:user:6", "1", 20L, TimeUnit.SECONDS)).thenReturn(true);
        when(coldStartRecommendService.recommend(6L, 10)).thenReturn(List.of(first, second));
        when(courseGraphRepository.getCourseReadinessBatch(6L, List.of(100L, 200L), 0.7d)).thenReturn(List.of());
        when(courseGraphRepository.findCourseKnowledgePointsBatch(List.of(100L, 200L))).thenReturn(List.of(kp));

        HybridRecommendResponseDTO result = hybridRecommendService.recommend(6L);

        assertEquals(1, result.getItems().get(0).getKnowledgePoints().size());
        assertEquals("集合", result.getItems().get(0).getKnowledgePoints().get(0).getName());
        assertTrue(result.getItems().get(1).getKnowledgePoints().isEmpty());
        verify(courseGraphRepository).findCourseKnowledgePointsBatch(List.of(100L, 200L));
    }

    @Test
    void recommendShouldUseHotFallbackWhenCacheMissAndLockIsNotAcquired() {
        // 这里故意模拟"没拿到构建锁且等待缓存也失败"的场景，
        // 触发热点兜底路径，验证最终仍能回源构建并写缓存。
        when(coldStartSupportService.isColdStartUser(1L)).thenReturn(false);
        when(valueOperations.get("recommend:user:1")).thenReturn((Object) null, (Object) null, (Object) null,
                (Object) null);
        when(valueOperations.setIfAbsent("recommend:lock:user:1", "1", 20L, TimeUnit.SECONDS)).thenReturn(false);
        when(recommendService.recommend(1L)).thenReturn(recommendResponseDto(List.of()));
        when(newCourseRecommendService.recommendForRegularUser(1L, 30)).thenReturn(List.of());
        when(learningAnalysisService.getHotCoursesByRange(0, 10)).thenReturn(List.of(9L, 10L, 8L));
        when(courseService.getOnlineRecommendCourseSummaryMapByIds(List.of(9L, 10L, 8L))).thenReturn(Map.of(
                9L, courseSummary(9L, "热门课 9", "cover-9", 1),
                8L, courseSummary(8L, "热门课 8", "cover-8", 2)));

        HybridRecommendResponseDTO result = hybridRecommendService.recommend(1L);

        assertEquals(List.of(9L, 8L), result.getItems().stream().map(HybridRecommendItemDTO::getCourseId).toList());
        assertEquals("HOT_FALLBACK", result.getItems().get(0).getRecommendSource());
        assertEquals("热门课程兜底：近期较多同学在学", result.getItems().get(0).getReason());
        // 热门兜底不看 finalScore，而是按最终列表位置给展示分梯度：
        // 第 0 位 -> 0.70 -> 85；第 1 位 -> 0.67 -> 83。
        assertEquals(85, result.getItems().get(0).getRecommendScore());
        assertEquals(83, result.getItems().get(1).getRecommendScore());
        assertTrue(result.getItems().get(0).getRecommendScore() > result.getItems().get(1).getRecommendScore());
        assertNotNull(result.getItems().get(0).getKnowledgePoints());
        assertNotNull(result.getItems().get(0).getLearningPaths());
        verify(recommendService).recommend(1L);
        verify(valueOperations).set(eq("recommend:user:1"), any(HybridRecommendResponseDTO.class), eq(30L),
                eq(TimeUnit.MINUTES));
    }

    @Test
    void recommendShouldContinueScanningHotFallbackUntilFilled() {
        // 热榜里允许混入无效或下线课程，因此热门兜底不能简单"只查第一批就结束"，
        // 必须按已扫描区间继续向后找，直到补够或热榜耗尽。
        when(coldStartSupportService.isColdStartUser(4L)).thenReturn(false);
        when(valueOperations.get("recommend:user:4")).thenReturn((Object) null, (Object) null);
        when(valueOperations.setIfAbsent("recommend:lock:user:4", "1", 20L, TimeUnit.SECONDS)).thenReturn(true);
        when(recommendService.recommend(4L)).thenReturn(recommendResponseDto(List.of()));
        when(newCourseRecommendService.recommendForRegularUser(4L, 30)).thenReturn(List.of());
        when(learningAnalysisService.getHotCoursesByRange(0, 10)).thenReturn(List.of(9L, 10L, 8L));
        when(learningAnalysisService.getHotCoursesByRange(3, 10)).thenReturn(List.of(11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L));
        when(courseService.getOnlineRecommendCourseSummaryMapByIds(List.of(9L, 10L, 8L))).thenReturn(Map.of(
                9L, courseSummary(9L, "热门课 9", "cover-9", 1),
                8L, courseSummary(8L, "热门课 8", "cover-8", 2)));
        when(courseService.getOnlineRecommendCourseSummaryMapByIds(List.of(11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L)))
                .thenReturn(Map.of(
                        11L, courseSummary(11L, "热门课 11", "cover-11", 1),
                        12L, courseSummary(12L, "热门课 12", "cover-12", 1),
                        13L, courseSummary(13L, "热门课 13", "cover-13", 1),
                        14L, courseSummary(14L, "热门课 14", "cover-14", 1),
                        15L, courseSummary(15L, "热门课 15", "cover-15", 1),
                        16L, courseSummary(16L, "热门课 16", "cover-16", 1),
                        17L, courseSummary(17L, "热门课 17", "cover-17", 1),
                        18L, courseSummary(18L, "热门课 18", "cover-18", 1),
                        19L, courseSummary(19L, "热门课 19", "cover-19", 1),
                        20L, courseSummary(20L, "热门课 20", "cover-20", 1)));

        HybridRecommendResponseDTO result = hybridRecommendService.recommend(4L);

        assertEquals(List.of(9L, 8L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L),
                result.getItems().stream().map(HybridRecommendItemDTO::getCourseId).toList());
        verify(learningAnalysisService).getHotCoursesByRange(0, 10);
        verify(learningAnalysisService).getHotCoursesByRange(3, 10);
        verify(learningAnalysisService, never()).getHotCoursesByRange(13, 10);
    }

    @Test
    void recommendShouldProduceSameResultWhenAsyncEnabledForRegularPath() {
        // 开启异步后，executor 在当前线程同步执行，CF + 新课候选的并行结果
        // 必须与串行路径完全一致：包括排序、新课插槽注入和展示分。
        ReflectionTestUtils.setField(hybridRecommendService, "asyncEnabled", true);

        HybridRecommendItemDTO newCourse = hybridItem(4L, "新课注入");
        newCourse.setRecommendSource("COLD_START_COURSE");
        newCourse.setIsNewCourse(Boolean.TRUE);

        when(coldStartSupportService.isColdStartUser(1L)).thenReturn(false);
        when(valueOperations.get("recommend:user:1")).thenReturn((Object) null, (Object) null);
        when(valueOperations.setIfAbsent("recommend:lock:user:1", "1", 20L, TimeUnit.SECONDS)).thenReturn(true);
        when(recommendService.recommend(1L)).thenReturn(recommendResponseDto(List.of(
                cfItem(1L, 10.0d),
                cfItem(2L, 8.0d),
                cfItem(3L, 6.0d))));
        when(newCourseRecommendService.recommendForRegularUser(1L, 30)).thenReturn(List.of(newCourse));
        when(courseService.getRecommendCourseSummaryMapByIds(List.of(1L, 2L, 3L))).thenReturn(Map.of(
                1L, courseSummary(1L, "CF 课程 1", "cover-1", 1),
                2L, courseSummary(2L, "CF 课程 2", "cover-2", 2),
                3L, courseSummary(3L, "CF 课程 3", "cover-3", 3)));
        when(courseGraphRepository.getCourseReadinessBatch(eq(1L), anyList(), eq(0.7d))).thenAnswer(invocation -> {
            List<Long> courseIds = invocation.getArgument(1);
            if (courseIds.equals(List.of(1L, 2L, 3L))) {
                return List.of(
                        readiness(1L, 0.2d),
                        readiness(2L, 1.0d),
                        readiness(3L, 0.8d));
            }
            if (courseIds.equals(List.of(4L))) {
                return List.of(readiness(4L, 0.6d));
            }
            return List.of();
        });

        HybridRecommendResponseDTO result = hybridRecommendService.recommend(1L);

        // 结果必须与串行测试 recommendShouldMergeCfAndNewCourseAndFetchMissingReadiness 完全一致。
        assertEquals(List.of(1L, 2L, 4L, 3L),
                result.getItems().stream().map(HybridRecommendItemDTO::getCourseId).toList());
        assertEquals(4, result.getItems().stream().map(HybridRecommendItemDTO::getCourseId).distinct().count());
        assertEquals(0.76d, result.getItems().get(0).getFinalScore(), 1e-9);
        assertEquals(List.of(87, 83, 92, 68),
                result.getItems().stream().map(HybridRecommendItemDTO::getRecommendScore).toList());
        assertEquals(0.6d, result.getItems().get(2).getReadiness());
        verify(courseGraphRepository, times(1)).getCourseReadinessBatch(1L, List.of(1L, 2L, 3L), 0.7d);
        verify(courseService, times(1)).getRecommendCourseSummaryMapByIds(List.of(1L, 2L, 3L));
    }

    @Test
    void recommendShouldProduceSameResultWhenAsyncEnabledForColdStartPath() {
        // 冷启动分支也通过 enrichGraphInfo 做图谱补全；异步开启后 exec 同步执行，
        // 需保证 readiness 补查、知识点、学习路径的填充结果与串行路径一致。
        ReflectionTestUtils.setField(hybridRecommendService, "asyncEnabled", true);

        ColdStartRecommendItemVO first = coldStartItem(100L, "课程 A", 1, 0.9d, "匹配兴趣标签：Java");
        ColdStartRecommendItemVO second = coldStartItem(200L, "课程 B", 1, 0.8d, "兜底");
        CourseKnowledgePointDTO kp = knowledgePointRow(100L, 11L, "集合", 2);

        when(coldStartSupportService.isColdStartUser(6L)).thenReturn(true);
        when(valueOperations.get("recommend:cold:user:6")).thenReturn((Object) null, (Object) null);
        when(valueOperations.setIfAbsent("recommend:cold:lock:user:6", "1", 20L, TimeUnit.SECONDS)).thenReturn(true);
        when(coldStartRecommendService.recommend(6L, 10)).thenReturn(List.of(first, second));
        when(courseGraphRepository.getCourseReadinessBatch(6L, List.of(100L, 200L), 0.7d)).thenReturn(List.of());
        when(courseGraphRepository.findCourseKnowledgePointsBatch(List.of(100L, 200L))).thenReturn(List.of(kp));

        HybridRecommendResponseDTO result = hybridRecommendService.recommend(6L);

        assertEquals(1, result.getItems().get(0).getKnowledgePoints().size());
        assertEquals("集合", result.getItems().get(0).getKnowledgePoints().get(0).getName());
        assertTrue(result.getItems().get(1).getKnowledgePoints().isEmpty());
    }

    @Test
    void recommendShouldNotCallNewCourseRecommendWhenNewCourseDisabledEvenIfAsyncEnabled() {
        // 新课开关关闭时，即使 asyncEnabled=true，也不创建异步任务，
        // 直接使用 List.of() 保持当前行为。
        ReflectionTestUtils.setField(hybridRecommendService, "asyncEnabled", true);
        ReflectionTestUtils.setField(hybridRecommendService, "newCourseRecommendEnabled", false);

        when(coldStartSupportService.isColdStartUser(3L)).thenReturn(false);
        when(valueOperations.get("recommend:user:3")).thenReturn((Object) null, (Object) null);
        when(valueOperations.setIfAbsent("recommend:lock:user:3", "1", 20L, TimeUnit.SECONDS)).thenReturn(true);
        when(recommendService.recommend(3L)).thenReturn(recommendResponseDto(List.of(cfItem(30L, 9.0d))));
        when(courseService.getRecommendCourseSummaryMapByIds(List.of(30L))).thenReturn(Map.of(
                30L, courseSummary(30L, "无图谱课程", "cover-30", 2)));
        when(courseGraphRepository.getCourseReadinessBatch(3L, List.of(30L), 0.7d)).thenReturn(List.of());

        HybridRecommendResponseDTO result = hybridRecommendService.recommend(3L);

        assertEquals(1, result.getItems().size());
        assertEquals(30L, result.getItems().get(0).getCourseId());
        assertEquals("CF", result.getItems().get(0).getRecommendSource());
        verify(newCourseRecommendService, never()).recommendForRegularUser(any(), any());
        // 新课开关关闭时注入上限为 0，不应尝试注入。
    }

    @Test
    void recommendShouldReturnEmptyWhenAllHotFallbackCoursesAreOffline() {
        // 如果热榜所有课程都被在线过滤挡掉，当前策略返回空列表；
        // 这个行为先保留，不在"统一展示分"这次改动里擅自改成其他兜底语义。
        when(coldStartSupportService.isColdStartUser(2L)).thenReturn(false);
        when(valueOperations.get("recommend:user:2")).thenReturn((Object) null, (Object) null);
        when(valueOperations.setIfAbsent("recommend:lock:user:2", "1", 20L, TimeUnit.SECONDS)).thenReturn(true);
        when(recommendService.recommend(2L)).thenReturn(recommendResponseDto(List.of()));
        when(newCourseRecommendService.recommendForRegularUser(2L, 30)).thenReturn(List.of());
        when(learningAnalysisService.getHotCoursesByRange(0, 10)).thenReturn(List.of(7L, 6L));
        when(learningAnalysisService.getHotCoursesByRange(2, 10)).thenReturn(List.of());
        when(courseService.getOnlineRecommendCourseSummaryMapByIds(List.of(7L, 6L))).thenReturn(Map.of());

        HybridRecommendResponseDTO result = hybridRecommendService.recommend(2L);

        assertTrue(result.getItems().isEmpty());
        verify(valueOperations).set(eq("recommend:user:2"), any(HybridRecommendResponseDTO.class), eq(30L),
                eq(TimeUnit.MINUTES));
    }

    private RecommendResponseDTO recommendResponseDto(List<RecommendItemDTO> items) {
        RecommendResponseDTO response = new RecommendResponseDTO();
        response.setUserId(1L);
        response.setItems(items);
        return response;
    }

    private RecommendItemDTO cfItem(Long courseId, Double score) {
        RecommendItemDTO dto = new RecommendItemDTO();
        dto.setCourseId(courseId);
        dto.setScore(score);
        return dto;
    }

    private HybridRecommendItemDTO hybridItem(Long courseId, String title) {
        HybridRecommendItemDTO item = new HybridRecommendItemDTO();
        item.setCourseId(courseId);
        item.setTitle(title);
        // 默认给 0.9，是为了让大部分测试在不额外关注分数细节时，
        // 也能稳定得到一个较高且易心算的展示分（92）。
        item.setFinalScore(0.9d);
        return item;
    }

    private Course courseSummary(Long courseId, String title, String coverUrl, Integer difficulty) {
        Course course = new Course();
        course.setId(courseId);
        course.setTitle(title);
        course.setCoverUrl(coverUrl);
        course.setDifficulty(difficulty);
        return course;
    }

    private ColdStartRecommendItemVO coldStartItem(Long courseId, String title, Integer difficulty, Double score,
            String reason) {
        ColdStartRecommendItemVO item = new ColdStartRecommendItemVO();
        item.setCourseId(courseId);
        item.setTitle(title);
        item.setDifficulty(difficulty);
        item.setCoverUrl("cover-" + title);
        item.setScore(score);
        item.setReason(reason);
        return item;
    }

    private CourseReadinessDTO readiness(Long courseId, Double readiness) {
        CourseReadinessDTO dto = new CourseReadinessDTO();
        dto.setCourseId(courseId);
        dto.setReadiness(readiness);
        dto.setMissing(List.of(new KnowledgeMasteryVO(10L, "先修", 1, 0.2d, 0.7d)));
        return dto;
    }

    private CourseKnowledgePointDTO knowledgePointRow(Long courseId, Long kpId, String name, Integer difficulty) {
        CourseKnowledgePointDTO dto = new CourseKnowledgePointDTO();
        dto.setCourseId(courseId);
        dto.setId(kpId);
        dto.setName(name);
        dto.setDifficulty(difficulty);
        return dto;
    }
}
