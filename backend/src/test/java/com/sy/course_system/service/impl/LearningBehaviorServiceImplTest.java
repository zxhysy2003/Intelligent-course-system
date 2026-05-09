package com.sy.course_system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.UserInfo;
import com.sy.course_system.entity.LearningBehavior;
import com.sy.course_system.entity.UserCourseRelation;
import com.sy.course_system.enums.LearnBehaviorType;
import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.recommend.RecommendCacheInvalidator;
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
    private RecommendCacheInvalidator recommendCacheInvalidator;
    @Mock
    private LearningBehaviorMapper learningBehaviorMapper;

    private LearningBehaviorServiceImpl learningBehaviorService;

    @BeforeEach
    void setUp() {
        learningBehaviorService = spy(new LearningBehaviorServiceImpl(courseService, knowledgeRepository,
                learningAnalysisService, userCourseService, videoService, stringRedisTemplate,
                recommendCacheInvalidator));
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
        verify(recommendCacheInvalidator).invalidateStrongUserRecommend(1L);
        verify(recommendCacheInvalidator, never()).invalidateStudyUserRecommend(1L);
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
        verify(recommendCacheInvalidator).invalidateStudyUserRecommend(1L);
        verify(recommendCacheInvalidator, never()).invalidateStrongUserRecommend(1L);

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
        verify(recommendCacheInvalidator).invalidateStrongUserRecommend(1L);
        verify(recommendCacheInvalidator, never()).invalidateStudyUserRecommend(1L);

        ArgumentCaptor<LearningBehavior> behaviorCaptor = ArgumentCaptor.forClass(LearningBehavior.class);
        verify(learningBehaviorService, org.mockito.Mockito.times(2)).save(behaviorCaptor.capture());
        assertEquals(List.of(LearnBehaviorType.STUDY, LearnBehaviorType.FINISH),
                behaviorCaptor.getAllValues().stream().map(LearningBehavior::getBehaviorType).toList());
    }

    @Test
    void recordBehaviorShouldThrottleInvalidateRecommendCacheOnNormalStudy() {
        UserCourseRelation active = relation(1, 120);
        doReturn(active).when(userCourseService).getUserCourseRelation(1L, 10L);
        doReturn(600).when(videoService).getVideoDurationInSeconds(10L);
        doReturn(0).when(userCourseService).tryMarkFinished(eq(1L), eq(10L), any(LocalDateTime.class));
        doReturn(true).when(learningBehaviorService).save(any(LearningBehavior.class));

        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.STUDY, 60);

        verify(userCourseService).addStudyTimeAndUpdateProgress(eq(1L), eq(10L), eq(60), eq(600),
                any(LocalDateTime.class));
        verify(recommendCacheInvalidator).invalidateStudyUserRecommend(1L);
        verify(recommendCacheInvalidator, never()).invalidateStrongUserRecommend(1L);
    }

    @Test
    void recordBehaviorShouldStrongInvalidateWhenFavoriteChanges() {
        UserCourseRelation relation = relation(1, 120);
        relation.setIsFavorite(0);
        doReturn(relation).when(userCourseService).getUserCourseRelation(1L, 10L);
        doReturn(true).when(learningBehaviorService).save(any(LearningBehavior.class));

        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.FAVORITE, null);

        assertEquals(1, relation.getIsFavorite());
        verify(userCourseService).updateUserCourseRelation(relation);
        verify(recommendCacheInvalidator).invalidateStrongUserRecommend(1L);
        verify(recommendCacheInvalidator, never()).invalidateStudyUserRecommend(1L);
        verify(learningAnalysisService).increaseCourseHot(10L, 5.0d);
    }

    @Test
    void recordBehaviorShouldStrongInvalidateWhenUnfavoriteChanges() {
        UserCourseRelation relation = relation(1, 120);
        relation.setIsFavorite(1);
        doReturn(relation).when(userCourseService).getUserCourseRelation(1L, 10L);

        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.UNFAVORITE, null);

        assertEquals(0, relation.getIsFavorite());
        verify(userCourseService).updateUserCourseRelation(relation);
        verify(recommendCacheInvalidator).invalidateStrongUserRecommend(1L);
        verify(recommendCacheInvalidator, never()).invalidateStudyUserRecommend(1L);
        verify(learningBehaviorService, never()).save(any(LearningBehavior.class));
    }

    @Test
    void recordBehaviorShouldStrongInvalidateWhenFinishHasNoKnowledgePoints() {
        UserCourseRelation before = relation(1, 540);

        doReturn(before).when(userCourseService).getUserCourseRelation(1L, 10L);
        doReturn(600).when(videoService).getVideoDurationInSeconds(10L);
        doReturn(1).when(userCourseService).tryMarkFinished(eq(1L), eq(10L), any(LocalDateTime.class));
        doReturn(List.of()).when(courseService).getKnowledgePointIdsByCourseId(10L);
        doReturn(true).when(learningBehaviorService).save(any(LearningBehavior.class));

        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.STUDY, 60);

        verify(recommendCacheInvalidator).invalidateStrongUserRecommend(1L);
        verify(recommendCacheInvalidator, never()).invalidateStudyUserRecommend(1L);
        verify(knowledgeRepository, never()).markUserMasteredBatch(any(), any(), any());
        verify(learningBehaviorService, org.mockito.Mockito.times(2)).save(any(LearningBehavior.class));
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
