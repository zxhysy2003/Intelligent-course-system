package com.sy.course_system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.UserInfo;
import com.sy.course_system.entity.LearningBehavior;
import com.sy.course_system.entity.UserCourseRelation;
import com.sy.course_system.enums.BehaviorRecordOutcome;
import com.sy.course_system.enums.LearnBehaviorType;
import com.sy.course_system.exception.LearningBehaviorEventConflictException;
import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.mapper.UserCourseRelationMapper;
import com.sy.course_system.outbox.LearningOutboxWriter;
import com.sy.course_system.outbox.LearningOutboxPayload;
import com.sy.course_system.recommend.RecommendCacheInvalidator;
import com.sy.course_system.repository.KnowledgeRepository;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.service.RecommendScoreSnapshotService;
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
    private RecommendScoreSnapshotService recommendScoreSnapshotService;
    @Mock
    private LearningBehaviorMapper learningBehaviorMapper;

    @Mock private UserCourseRelationMapper relationMapper;
    @Mock private LearningOutboxWriter outbox;
    private LearningBehaviorServiceImpl learningBehaviorService;

    @BeforeEach
    void setUp() {
        learningBehaviorService = spy(new LearningBehaviorServiceImpl(courseService, userCourseService, videoService, relationMapper, outbox));
        ReflectionTestUtils.setField(learningBehaviorService, "baseMapper", learningBehaviorMapper);
        lenient().doReturn(1).when(learningBehaviorMapper).insertStudyIfAbsent(any(LearningBehavior.class));
        lenient().doReturn(true).when(learningBehaviorService).save(any(LearningBehavior.class));
        UserContext.set(new UserInfo(1L, "student", "USER"));
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        UserContext.clear();
    }

    @Test
    void recordBehaviorShouldTriggerFinishWhenStudyFirstReachesCompletion() {
        UserCourseRelation before = relation(1, 540);
        UserCourseRelation after = relation(2, 600);

        doReturn(before, after).when(relationMapper).selectForUpdate(1L, 10L);
        doReturn(600).when(videoService).getVideoDurationInSeconds(10L);
        doReturn(1).when(userCourseService).tryMarkFinished(eq(1L), eq(10L), any(LocalDateTime.class));
        doReturn(List.of(100L, 101L)).when(courseService).getKnowledgePointIdsByCourseId(10L);
        doReturn(40.0).when(learningBehaviorMapper).getUserCourseBaseScore(1L, 10L);
        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.STUDY, 60, "study-event-1");

        InOrder writeOrder = inOrder(learningBehaviorMapper, userCourseService);
        writeOrder.verify(learningBehaviorMapper).insertStudyIfAbsent(any(LearningBehavior.class));
        writeOrder.verify(userCourseService).addStudyTimeAndUpdateProgress(
                eq(1L), eq(10L), eq(60), eq(600), any(LocalDateTime.class));
        verify(userCourseService).addStudyTimeAndUpdateProgress(eq(1L), eq(10L), eq(60), eq(600),
                any(LocalDateTime.class));
        verify(userCourseService).tryMarkFinished(eq(1L), eq(10L), any(LocalDateTime.class));
        verify(outbox).enqueue(eq(1L), eq(10L), org.mockito.ArgumentMatchers.argThat(p -> p.mastery() == 1.0 && p.knowledgePointIds().equals(List.of(100L,101L))), eq(true), eq(true));
        verify(outbox).enqueue(eq(1L), eq(10L), org.mockito.ArgumentMatchers.argThat(p -> "strong".equals(p.invalidationMode())), any(Boolean.class), any(Boolean.class));
        verify(recommendCacheInvalidator, never()).invalidateStudyUserRecommend(1L);
        verify(outbox).enqueue(eq(1L), eq(10L), org.mockito.ArgumentMatchers.argThat(p -> Math.abs(p.hotDelta() - (2.0 + 1.0/30)) < 1e-9), eq(true), eq(true));

        ArgumentCaptor<LearningBehavior> studyCaptor = ArgumentCaptor.forClass(LearningBehavior.class);
        verify(learningBehaviorMapper).insertStudyIfAbsent(studyCaptor.capture());
        assertEquals("study-event-1", studyCaptor.getValue().getEventId());
        assertEquals(LearnBehaviorType.STUDY, studyCaptor.getValue().getBehaviorType());

        ArgumentCaptor<LearningBehavior> finishCaptor = ArgumentCaptor.forClass(LearningBehavior.class);
        verify(learningBehaviorService).save(finishCaptor.capture());
        assertEquals(LearnBehaviorType.FINISH, finishCaptor.getValue().getBehaviorType());
    }

    @Test
    void recordBehaviorShouldNotTriggerFinishAgainWhenCourseAlreadyCompleted() {
        UserCourseRelation completed = relation(2, 600);
        completed.setCompleteTime(LocalDateTime.now());
        doReturn(completed).when(relationMapper).selectForUpdate(1L, 10L);
        doReturn(600).when(videoService).getVideoDurationInSeconds(10L);
        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.STUDY, 60, "study-event-1");

        verify(userCourseService).addStudyTimeAndUpdateProgress(eq(1L), eq(10L), eq(60), eq(600),
                any(LocalDateTime.class));
        verify(userCourseService, never()).tryMarkFinished(eq(1L), eq(10L), any(LocalDateTime.class));
        verify(courseService, never()).getKnowledgePointIdsByCourseId(10L);
        verify(knowledgeRepository, never()).markUserMasteredBatch(any(), any(), any());
        verify(outbox).enqueue(eq(1L), eq(10L), org.mockito.ArgumentMatchers.argThat(p -> "soft".equals(p.invalidationMode())), eq(true), eq(false));
        verify(recommendCacheInvalidator, never()).invalidateStrongUserRecommend(1L);

        verify(learningBehaviorMapper).insertStudyIfAbsent(any(LearningBehavior.class));
        verify(learningBehaviorService, never()).save(any(LearningBehavior.class));
    }

    @Test
    void recordBehaviorShouldRepairLegacyCompletedRelationWithoutCompleteTime() {
        UserCourseRelation legacyCompleted = relation(2, 600);
        UserCourseRelation repaired = relation(2, 600);
        repaired.setCompleteTime(LocalDateTime.now());

        doReturn(legacyCompleted, repaired).when(relationMapper).selectForUpdate(1L, 10L);
        doReturn(600).when(videoService).getVideoDurationInSeconds(10L);
        doReturn(1).when(userCourseService).tryMarkFinished(eq(1L), eq(10L), any(LocalDateTime.class));
        doReturn(List.of(100L, 101L)).when(courseService).getKnowledgePointIdsByCourseId(10L);
        doReturn(40.0).when(learningBehaviorMapper).getUserCourseBaseScore(1L, 10L);
        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.STUDY, 60, "study-event-1");

        verify(userCourseService).tryMarkFinished(eq(1L), eq(10L), any(LocalDateTime.class));
        verify(outbox).enqueue(eq(1L), eq(10L), org.mockito.ArgumentMatchers.argThat(p -> p.mastery() == 1.0 && p.knowledgePointIds().equals(List.of(100L,101L))), eq(true), eq(true));
        verify(outbox).enqueue(eq(1L), eq(10L), org.mockito.ArgumentMatchers.argThat(p -> "strong".equals(p.invalidationMode())), any(Boolean.class), any(Boolean.class));
        verify(recommendCacheInvalidator, never()).invalidateStudyUserRecommend(1L);

        verify(learningBehaviorMapper).insertStudyIfAbsent(any(LearningBehavior.class));
        ArgumentCaptor<LearningBehavior> finishCaptor = ArgumentCaptor.forClass(LearningBehavior.class);
        verify(learningBehaviorService).save(finishCaptor.capture());
        assertEquals(LearnBehaviorType.FINISH, finishCaptor.getValue().getBehaviorType());
    }

    @Test
    void recordBehaviorShouldThrottleInvalidateRecommendCacheOnNormalStudy() {
        UserCourseRelation active = relation(1, 120);
        doReturn(active).when(relationMapper).selectForUpdate(1L, 10L);
        doReturn(600).when(videoService).getVideoDurationInSeconds(10L);
        doReturn(0).when(userCourseService).tryMarkFinished(eq(1L), eq(10L), any(LocalDateTime.class));

        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.STUDY, 60, "study-event-1");

        verify(userCourseService).addStudyTimeAndUpdateProgress(eq(1L), eq(10L), eq(60), eq(600),
                any(LocalDateTime.class));
        verify(outbox).enqueue(eq(1L), eq(10L), org.mockito.ArgumentMatchers.argThat(p -> "soft".equals(p.invalidationMode())), eq(true), eq(false));
        verify(recommendCacheInvalidator, never()).invalidateStrongUserRecommend(1L);
    }

    @Test
    void recordBehaviorShouldStrongInvalidateWhenFavoriteChanges() {
        UserCourseRelation relation = relation(1, 120);
        relation.setIsFavorite(0);
        doReturn(relation).when(relationMapper).selectForUpdate(1L, 10L);
        doReturn(true).when(learningBehaviorService).save(any(LearningBehavior.class));

        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.FAVORITE, null, null);

        assertEquals(1, relation.getIsFavorite());
        verify(userCourseService).updateUserCourseRelation(relation);
        verify(outbox).enqueue(eq(1L), eq(10L), org.mockito.ArgumentMatchers.argThat(p -> "strong".equals(p.invalidationMode())), any(Boolean.class), any(Boolean.class));
        verify(recommendCacheInvalidator, never()).invalidateStudyUserRecommend(1L);
        verify(outbox).enqueue(eq(1L), eq(10L), org.mockito.ArgumentMatchers.argThat(p -> p.hotDelta() == 5.0), eq(true), eq(false));
    }

    @Test
    void recordBehaviorShouldStrongInvalidateWhenUnfavoriteChanges() {
        UserCourseRelation relation = relation(1, 120);
        relation.setIsFavorite(1);
        doReturn(relation).when(relationMapper).selectForUpdate(1L, 10L);

        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.UNFAVORITE, null, null);

        assertEquals(0, relation.getIsFavorite());
        verify(userCourseService).updateUserCourseRelation(relation);
        verify(outbox).enqueue(eq(1L), eq(10L), org.mockito.ArgumentMatchers.argThat(p -> "strong".equals(p.invalidationMode())), any(Boolean.class), any(Boolean.class));
        verify(recommendCacheInvalidator, never()).invalidateStudyUserRecommend(1L);
        verify(learningBehaviorService, never()).save(any(LearningBehavior.class));
    }

    @Test
    void recordBehaviorShouldStrongInvalidateWhenFinishHasNoKnowledgePoints() {
        UserCourseRelation before = relation(1, 540);

        doReturn(before).when(relationMapper).selectForUpdate(1L, 10L);
        doReturn(600).when(videoService).getVideoDurationInSeconds(10L);
        doReturn(1).when(userCourseService).tryMarkFinished(eq(1L), eq(10L), any(LocalDateTime.class));
        doReturn(List.of()).when(courseService).getKnowledgePointIdsByCourseId(10L);
        doReturn(true).when(learningBehaviorService).save(any(LearningBehavior.class));

        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.STUDY, 60, "study-event-1");

        verify(outbox).enqueue(eq(1L), eq(10L), org.mockito.ArgumentMatchers.argThat(p -> "strong".equals(p.invalidationMode())), any(Boolean.class), any(Boolean.class));
        verify(recommendCacheInvalidator, never()).invalidateStudyUserRecommend(1L);
        verify(knowledgeRepository, never()).markUserMasteredBatch(any(), any(), any());
        verify(learningBehaviorMapper).insertStudyIfAbsent(any(LearningBehavior.class));
        verify(learningBehaviorService).save(any(LearningBehavior.class));
    }

    @Test
    void taskInsertFailureShouldPropagateInsteadOfLosingSideEffects() {
        UserCourseRelation relation = relation(1, 120);
        doReturn(relation).when(relationMapper).selectForUpdate(1L, 10L);
        doReturn(true).when(learningBehaviorService).save(any(LearningBehavior.class));
        doThrow(new RuntimeException("outbox unavailable")).when(outbox)
                .enqueue(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean());
        assertThrows(RuntimeException.class, () -> learningBehaviorService.recordBehavior(10L, LearnBehaviorType.FAVORITE, null, null));
    }

    @Test
    void viewCooldownShouldUseMysqlAndProduceNoTaskWhenCooling() {
        UserCourseRelation relation = relation(1, 120);
        relation.setLastViewRecordedAt(LocalDateTime.now());
        doReturn(relation).when(relationMapper).selectForUpdate(1L, 10L);
        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.VIEW, null, null);
        verifyNoInteractions(outbox, stringRedisTemplate);
        verify(learningBehaviorService, never()).save(any(LearningBehavior.class));
    }

    @Test
    void viewShouldRecordCooldownAndTaskTogether() {
        UserCourseRelation relation = relation(1, 120);
        relation.setId(99L);
        doReturn(relation).when(relationMapper).selectForUpdate(1L, 10L);
        doReturn(1).when(relationMapper).updateViewTime(eq(99L), any());
        doReturn(true).when(learningBehaviorService).save(any(LearningBehavior.class));
        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.VIEW, null, null);
        verify(outbox).enqueue(eq(1L), eq(10L), org.mockito.ArgumentMatchers.argThat(p -> p.hotDelta()==0.5), eq(true), eq(false));
        verifyNoInteractions(stringRedisTemplate, knowledgeRepository, learningAnalysisService);
    }

    @Test
    void repeatedFavoriteShouldProduceNoTask() {
        UserCourseRelation relation = relation(1, 120);
        relation.setIsFavorite(1);
        doReturn(relation).when(relationMapper).selectForUpdate(1L, 10L);
        learningBehaviorService.recordBehavior(10L, LearnBehaviorType.FAVORITE, null, null);
        verifyNoInteractions(outbox);
    }

    @Test
    void recordBehaviorShouldRejectDirectFinish() {
        assertThrows(IllegalArgumentException.class,
                () -> learningBehaviorService.recordBehavior(10L, LearnBehaviorType.FINISH, null, null));

        verifyNoInteractions(userCourseService, recommendScoreSnapshotService, learningAnalysisService);
        verify(learningBehaviorService, never()).save(any(LearningBehavior.class));
    }

    @Test
    void recordBehaviorShouldReplaySameStudyEventWithoutRepeatingSideEffects() {
        UserCourseRelation active = relation(1, 120);
        LearningBehavior existing = behavior(10L, 60, "study-event-1");
        doReturn(active).when(relationMapper).selectForUpdate(1L, 10L);
        doReturn(0).when(learningBehaviorMapper).insertStudyIfAbsent(any(LearningBehavior.class));
        doReturn(existing).when(learningBehaviorMapper)
                .selectByUserIdAndEventIdForShare(1L, "study-event-1");

        BehaviorRecordOutcome outcome = learningBehaviorService.recordBehavior(
                10L, LearnBehaviorType.STUDY, 60, "study-event-1");

        assertEquals(BehaviorRecordOutcome.REPLAYED, outcome);
        verify(userCourseService, never()).addStudyTimeAndUpdateProgress(any(), any(), any(), any(), any());
        verify(learningBehaviorService, never()).save(any(LearningBehavior.class));
        verifyNoInteractions(videoService, learningAnalysisService, recommendCacheInvalidator,
                recommendScoreSnapshotService, knowledgeRepository, outbox);
    }

    @Test
    void recordBehaviorShouldRejectReusedEventIdWithDifferentPayload() {
        UserCourseRelation active = relation(1, 120);
        LearningBehavior existing = behavior(10L, 30, "study-event-1");
        doReturn(active).when(relationMapper).selectForUpdate(1L, 10L);
        doReturn(0).when(learningBehaviorMapper).insertStudyIfAbsent(any(LearningBehavior.class));
        doReturn(existing).when(learningBehaviorMapper)
                .selectByUserIdAndEventIdForShare(1L, "study-event-1");

        assertThrows(LearningBehaviorEventConflictException.class,
                () -> learningBehaviorService.recordBehavior(
                        10L, LearnBehaviorType.STUDY, 60, "study-event-1"));

        verify(userCourseService, never()).addStudyTimeAndUpdateProgress(any(), any(), any(), any(), any());
        verifyNoInteractions(videoService, learningAnalysisService, recommendCacheInvalidator,
                recommendScoreSnapshotService, knowledgeRepository, outbox);
    }

    @Test
    void recordBehaviorShouldRequireValidStudyEventId() {
        assertThrows(IllegalArgumentException.class,
                () -> learningBehaviorService.recordBehavior(10L, LearnBehaviorType.STUDY, 60, null));
        assertThrows(IllegalArgumentException.class,
                () -> learningBehaviorService.recordBehavior(10L, LearnBehaviorType.STUDY, 60, "bad event"));

        verifyNoInteractions(userCourseService, learningBehaviorMapper, videoService, learningAnalysisService);
    }

    private UserCourseRelation relation(Integer status, Integer learnedSeconds) {
        UserCourseRelation relation = new UserCourseRelation();
        relation.setUserId(1L);
        relation.setCourseId(10L);
        relation.setStatus(status);
        relation.setLearnedSeconds(learnedSeconds);
        return relation;
    }

    private LearningBehavior behavior(Long courseId, Integer duration, String eventId) {
        LearningBehavior behavior = new LearningBehavior();
        behavior.setUserId(1L);
        behavior.setCourseId(courseId);
        behavior.setEventId(eventId);
        behavior.setBehaviorType(LearnBehaviorType.STUDY);
        behavior.setDuration(duration);
        return behavior;
    }
}
