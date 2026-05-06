package com.sy.course_system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.UserInfo;
import com.sy.course_system.entity.UserCourseRelation;
import com.sy.course_system.mapper.UserCourseRelationMapper;

@ExtendWith(MockitoExtension.class)
class UserCourseServiceImplTest {

    @Mock
    private UserCourseRelationMapper userCourseRelationMapper;

    @Spy
    @InjectMocks
    private UserCourseServiceImpl userCourseService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userCourseService, "baseMapper", userCourseRelationMapper);
        UserContext.set(new UserInfo(9L, "student", "USER"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void userAttendCourseShouldInsertInitialRelation() {
        doReturn(true).when(userCourseService).save(any(UserCourseRelation.class));

        Boolean result = userCourseService.userAttendCourse(20L);

        assertTrue(result);
        ArgumentCaptor<UserCourseRelation> captor = ArgumentCaptor.forClass(UserCourseRelation.class);
        verify(userCourseService).save(captor.capture());
        UserCourseRelation relation = captor.getValue();
        assertEquals(9L, relation.getUserId());
        assertEquals(20L, relation.getCourseId());
        assertEquals(0, relation.getProgress());
        assertEquals(0, relation.getLearnedSeconds());
        assertEquals(0, relation.getStatus());
        assertNotNull(relation.getLastLearnTime());
        assertNull(relation.getCompleteTime());
        assertEquals(0, relation.getIsFavorite());
        assertEquals(0, relation.getProgressSeconds());
    }

    @Test
    void userAttendCourseShouldReturnFalseWhenRelationAlreadyExists() {
        doThrow(new DuplicateKeyException("duplicate user course"))
                .when(userCourseService).save(any(UserCourseRelation.class));

        Boolean result = userCourseService.userAttendCourse(20L);

        assertFalse(result);
        verify(userCourseService).save(any(UserCourseRelation.class));
    }

    @Test
    void listSelectedCourseIdsShouldReturnEmptyWhenCourseIdsIsNull() {
        List<Long> result = userCourseService.listSelectedCourseIds(9L, null);
        assertTrue(result.isEmpty());
        verify(userCourseRelationMapper, never()).selectSelectedCourseIds(any(), any());
    }

    @Test
    void listSelectedCourseIdsShouldReturnEmptyWhenCourseIdsIsEmpty() {
        List<Long> result = userCourseService.listSelectedCourseIds(9L, List.of());
        assertTrue(result.isEmpty());
        verify(userCourseRelationMapper, never()).selectSelectedCourseIds(any(), any());
    }

    @Test
    void listSelectedCourseIdsShouldReturnMapperResultForNonEmptyInput() {
        doReturn(List.of(1L, 3L))
                .when(userCourseRelationMapper).selectSelectedCourseIds(9L, List.of(1L, 2L, 3L));

        List<Long> result = userCourseService.listSelectedCourseIds(9L, List.of(1L, 2L, 3L));

        assertEquals(List.of(1L, 3L), result);
        verify(userCourseRelationMapper).selectSelectedCourseIds(9L, List.of(1L, 2L, 3L));
    }
}
