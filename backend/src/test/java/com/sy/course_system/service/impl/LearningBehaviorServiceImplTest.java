package com.sy.course_system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.UserInfo;
import com.sy.course_system.entity.LearningBehavior;
import com.sy.course_system.entity.UserCourseRelation;
import com.sy.course_system.enums.LearnBehaviorType;
import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.repository.KnowledgeRepository;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.service.UserCourseService;
import com.sy.course_system.service.VideoService;

@ExtendWith(MockitoExtension.class)
class LearningBehaviorServiceImplTest {

    @Mock
    private CourseService courseService;
    @Mock
    private KnowledgeRepository knowledgeRepository;
    @Mock
    private LearningAnalysisService learningAnalysisService;
    @Mock
    private UserCourseService userCourseService;
    @Mock
    private VideoService videoService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private LearningBehaviorMapper learningBehaviorMapper;

    @Spy
    @InjectMocks
    private LearningBehaviorServiceImpl learningBehaviorService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(learningBehaviorService, "baseMapper", learningBehaviorMapper);
        UserContext.set(new UserInfo(1L, "student", "USER"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void recordBehaviorShouldTriggerFinishWhenStudyFirstReachesCompletion() {
        UserCourseRelation before = relation(1, 540);
        UserCourseRelation after = relation(2, 600);

        doReturn(before, after).when(userCourseService).getUserCourseRelation(1L, 10L);
        doReturn(600).when(videoService).getVideoDurationInSeconds(10L);
        doReturn(1).when(userCourseService).tryMarkFinished(eq(1L), eq(10L), any(LocalDateTime.class));
        doReturn(List.of(100L, 101L)).when(courseService).getKnowledgePointIdsByCourseId(10L);
        doReturn(40.0).when(learningBehaviorMapper).getUserCourseBaseScore(1L, 10L);
        doReturn(true).when(learningBehaviorService).save(any(LearningBehavior.class));

        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.STUDY, 60);

        verify(userCourseService).addStudyTimeAndUpdateProgress(eq(1L), eq(10L), eq(60), eq(600),
                any(LocalDateTime.class));
        verify(userCourseService).tryMarkFinished(eq(1L), eq(10L), any(LocalDateTime.class));
        verify(knowledgeRepository).markUserMasteredBatch(1L, List.of(100L, 101L), 1.0d);
        verify(learningAnalysisService).refreshUserRecommendCache(1L);
        verify(learningAnalysisService).increaseCourseHot(10L, 2.0d);

        ArgumentCaptor<LearningBehavior> behaviorCaptor = ArgumentCaptor.forClass(LearningBehavior.class);
        verify(learningBehaviorService, org.mockito.Mockito.times(2)).save(behaviorCaptor.capture());
        assertEquals(List.of(LearnBehaviorType.STUDY, LearnBehaviorType.FINISH),
                behaviorCaptor.getAllValues().stream().map(LearningBehavior::getBehaviorType).toList());
    }

    @Test
    void recordBehaviorShouldNotTriggerFinishAgainWhenCourseAlreadyCompleted() {
        UserCourseRelation completed = relation(2, 600);
        completed.setCompleteTime(LocalDateTime.now());
        doReturn(completed).when(userCourseService).getUserCourseRelation(1L, 10L);
        doReturn(600).when(videoService).getVideoDurationInSeconds(10L);
        doReturn(true).when(learningBehaviorService).save(any(LearningBehavior.class));

        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.STUDY, 60);

        verify(userCourseService).addStudyTimeAndUpdateProgress(eq(1L), eq(10L), eq(60), eq(600),
                any(LocalDateTime.class));
        verify(userCourseService, never()).tryMarkFinished(eq(1L), eq(10L), any(LocalDateTime.class));
        verify(courseService, never()).getKnowledgePointIdsByCourseId(10L);
        verify(knowledgeRepository, never()).markUserMasteredBatch(any(), any(), any());
        verify(learningAnalysisService, never()).refreshUserRecommendCache(1L);

        ArgumentCaptor<LearningBehavior> behaviorCaptor = ArgumentCaptor.forClass(LearningBehavior.class);
        verify(learningBehaviorService).save(behaviorCaptor.capture());
        assertEquals(LearnBehaviorType.STUDY, behaviorCaptor.getValue().getBehaviorType());
    }

    @Test
    void recordBehaviorShouldRepairLegacyCompletedRelationWithoutCompleteTime() {
        UserCourseRelation legacyCompleted = relation(2, 600);
        UserCourseRelation repaired = relation(2, 600);
        repaired.setCompleteTime(LocalDateTime.now());

        doReturn(legacyCompleted, repaired).when(userCourseService).getUserCourseRelation(1L, 10L);
        doReturn(600).when(videoService).getVideoDurationInSeconds(10L);
        doReturn(1).when(userCourseService).tryMarkFinished(eq(1L), eq(10L), any(LocalDateTime.class));
        doReturn(List.of(100L, 101L)).when(courseService).getKnowledgePointIdsByCourseId(10L);
        doReturn(40.0).when(learningBehaviorMapper).getUserCourseBaseScore(1L, 10L);
        doReturn(true).when(learningBehaviorService).save(any(LearningBehavior.class));

        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.STUDY, 60);

        verify(userCourseService).tryMarkFinished(eq(1L), eq(10L), any(LocalDateTime.class));
        verify(knowledgeRepository).markUserMasteredBatch(1L, List.of(100L, 101L), 1.0d);
        verify(learningAnalysisService).refreshUserRecommendCache(1L);

        ArgumentCaptor<LearningBehavior> behaviorCaptor = ArgumentCaptor.forClass(LearningBehavior.class);
        verify(learningBehaviorService, org.mockito.Mockito.times(2)).save(behaviorCaptor.capture());
        assertEquals(List.of(LearnBehaviorType.STUDY, LearnBehaviorType.FINISH),
                behaviorCaptor.getAllValues().stream().map(LearningBehavior::getBehaviorType).toList());
    }

    private UserCourseRelation relation(Integer status, Integer learnedSeconds) {
        UserCourseRelation relation = new UserCourseRelation();
        relation.setUserId(1L);
        relation.setCourseId(10L);
        relation.setStatus(status);
        relation.setLearnedSeconds(learnedSeconds);
        return relation;
    }
}
