package com.sy.course_system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import com.sy.course_system.dto.onboarding.OnboardingSubmitDTO;
import com.sy.course_system.entity.Tag;
import com.sy.course_system.entity.UserInterestTag;
import com.sy.course_system.entity.UserOnboardingProfile;
import com.sy.course_system.mapper.TagMapper;
import com.sy.course_system.mapper.UserInterestTagMapper;
import com.sy.course_system.mapper.UserOnboardingProfileMapper;

@ExtendWith(MockitoExtension.class)
class OnboardingServiceImplTest {

    @Mock
    private TagMapper tagMapper;
    @Mock
    private UserOnboardingProfileMapper userOnboardingProfileMapper;
    @Mock
    private UserInterestTagMapper userInterestTagMapper;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private OnboardingServiceImpl onboardingService;

    @Test
    void submitShouldInsertProfileRebuildInitTagsAndDeleteBothRecommendCaches() {
        OnboardingSubmitDTO submitDTO = submitDTO(2, "PROJECT", List.of(101L, 102L));
        when(userOnboardingProfileMapper.selectByUserId(1L)).thenReturn(null);
        when(tagMapper.selectOnboardingTagsByIds(List.of(101L, 102L))).thenReturn(List.of(tag(101), tag(102)));

        onboardingService.submit(1L, submitDTO);

        ArgumentCaptor<UserOnboardingProfile> profileCaptor = ArgumentCaptor.forClass(UserOnboardingProfile.class);
        verify(userOnboardingProfileMapper).insert(profileCaptor.capture());
        UserOnboardingProfile inserted = profileCaptor.getValue();
        assertEquals(1L, inserted.getUserId());
        assertEquals(2, inserted.getCurrentLevel());
        assertEquals("PROJECT", inserted.getLearningGoal());
        assertEquals(1, inserted.getOnboardingStatus());

        verify(userInterestTagMapper).deleteByUserIdAndSource(1L, "INIT");
        ArgumentCaptor<List<UserInterestTag>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(userInterestTagMapper).batchInsert(rowsCaptor.capture());
        List<UserInterestTag> rows = rowsCaptor.getValue();
        assertEquals(2, rows.size());
        assertEquals(List.of(101L, 102L), rows.stream().map(UserInterestTag::getTagId).toList());
        assertEquals(List.of("INIT", "INIT"), rows.stream().map(UserInterestTag::getSource).toList());
        assertEquals(List.of(1.0, 1.0), rows.stream().map(UserInterestTag::getWeight).toList());

        verify(redisTemplate).delete("recommend:cold:user:1");
        verify(redisTemplate).delete("recommend:user:1");
    }

    @Test
    void submitShouldUpdateExistingProfileAndDeleteBothRecommendCaches() {
        OnboardingSubmitDTO submitDTO = submitDTO(3, "FOUNDATION", List.of(201L));
        UserOnboardingProfile profile = new UserOnboardingProfile();
        profile.setId(9L);
        profile.setUserId(2L);
        profile.setCurrentLevel(1);
        profile.setLearningGoal("JOB");
        profile.setOnboardingStatus(1);

        when(userOnboardingProfileMapper.selectByUserId(2L)).thenReturn(profile);
        when(tagMapper.selectOnboardingTagsByIds(List.of(201L))).thenReturn(List.of(tag(201)));

        onboardingService.submit(2L, submitDTO);

        ArgumentCaptor<UserOnboardingProfile> profileCaptor = ArgumentCaptor.forClass(UserOnboardingProfile.class);
        verify(userOnboardingProfileMapper).updateById(profileCaptor.capture());
        UserOnboardingProfile updated = profileCaptor.getValue();
        assertEquals(9L, updated.getId());
        assertEquals(3, updated.getCurrentLevel());
        assertEquals("FOUNDATION", updated.getLearningGoal());
        assertEquals(1, updated.getOnboardingStatus());

        verify(redisTemplate).delete("recommend:cold:user:2");
        verify(redisTemplate).delete("recommend:user:2");
    }

    @Test
    void submitShouldThrowWhenDtoIsNullAndNotDeleteCaches() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> onboardingService.submit(3L, null));

        assertEquals("请求参数不能为空", ex.getMessage());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void submitShouldThrowWhenCurrentLevelIsInvalidAndNotDeleteCaches() {
        OnboardingSubmitDTO submitDTO = submitDTO(4, "JOB", List.of(1L));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> onboardingService.submit(3L, submitDTO));

        assertEquals("currentLevel 非法", ex.getMessage());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void submitShouldThrowWhenTagIdsAreEmptyAndNotDeleteCaches() {
        OnboardingSubmitDTO submitDTO = submitDTO(2, "JOB", List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> onboardingService.submit(3L, submitDTO));

        assertEquals("tagIds 不能为空", ex.getMessage());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void submitShouldThrowWhenLearningGoalIsInvalidAndNotDeleteCaches() {
        OnboardingSubmitDTO submitDTO = submitDTO(2, "UNKNOWN", List.of(1L));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> onboardingService.submit(3L, submitDTO));

        assertEquals("learningGoal 非法", ex.getMessage());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void submitShouldThrowWhenOnboardingTagsAreInvalidAndNotDeleteCaches() {
        OnboardingSubmitDTO submitDTO = submitDTO(2, "EXAM", List.of(1L, 2L));
        when(tagMapper.selectOnboardingTagsByIds(List.of(1L, 2L))).thenReturn(List.of(tag(1)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> onboardingService.submit(3L, submitDTO));

        assertEquals("tagIds 包含不存在或不支持的标签", ex.getMessage());
        verify(userOnboardingProfileMapper, never()).insert(any());
        verify(userOnboardingProfileMapper, never()).updateById(any());
        verify(userInterestTagMapper, never()).deleteByUserIdAndSource(any(), anyString());
        verify(userInterestTagMapper, never()).batchInsert(anyList());
        verify(redisTemplate, never()).delete(anyString());
    }

    private OnboardingSubmitDTO submitDTO(Integer currentLevel, String learningGoal, List<Long> tagIds) {
        OnboardingSubmitDTO dto = new OnboardingSubmitDTO();
        dto.setCurrentLevel(currentLevel);
        dto.setLearningGoal(learningGoal);
        dto.setTagIds(tagIds);
        return dto;
    }

    private Tag tag(int id) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setStatus(1);
        return tag;
    }
}
