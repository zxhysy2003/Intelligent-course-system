package com.sy.course_system.service.impl;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sy.course_system.dto.course.TagOptionDTO;
import com.sy.course_system.dto.onboarding.OnboardingSubmitDTO;
import com.sy.course_system.entity.Tag;
import com.sy.course_system.entity.UserInterestTag;
import com.sy.course_system.entity.UserOnboardingProfile;
import com.sy.course_system.mapper.TagMapper;
import com.sy.course_system.mapper.UserInterestTagMapper;
import com.sy.course_system.mapper.UserOnboardingProfileMapper;
import com.sy.course_system.service.OnboardingService;
import com.sy.course_system.vo.OnboardingLearningGoalOptionVO;
import com.sy.course_system.vo.OnboardingLevelOptionVO;
import com.sy.course_system.vo.OnboardingOptionsVO;
import com.sy.course_system.vo.OnboardingStatusVO;

@Service
public class OnboardingServiceImpl implements OnboardingService {

    private static final String INIT_SOURCE = "INIT";
    private static final String COLD_START_RECOMMEND_KEY = "recommend:cold:user:";
    private static final Set<Integer> ALLOWED_LEVELS = Set.of(1, 2, 3);
    private static final Set<String> ALLOWED_GOALS = Set.of("JOB", "PROJECT", "FOUNDATION", "EXAM");

    @Autowired
    private TagMapper tagMapper;
    @Autowired
    private UserOnboardingProfileMapper userOnboardingProfileMapper;
    @Autowired
    private UserInterestTagMapper userInterestTagMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public OnboardingOptionsVO getOptions() {
        List<TagOptionDTO> tags = tagMapper.listEnabledOnboardingTagOptions();

        OnboardingOptionsVO vo = new OnboardingOptionsVO();
        vo.setTags(tags);
        vo.setLevels(buildLevelOptions());
        vo.setLearningGoals(buildLearningGoalOptions());
        return vo;
    }

    @Override
    @Transactional(transactionManager = "transactionManager")
    public void submit(Long userId, OnboardingSubmitDTO submitDTO) {
        if (submitDTO == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (submitDTO.getCurrentLevel() == null) {
            throw new IllegalArgumentException("currentLevel 不能为空");
        }
        if (!ALLOWED_LEVELS.contains(submitDTO.getCurrentLevel())) {
            throw new IllegalArgumentException("currentLevel 非法");
        }

        List<Long> tagIds = submitDTO.getTagIds() == null ? List.of() : submitDTO.getTagIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (tagIds.isEmpty()) {
            throw new IllegalArgumentException("tagIds 不能为空");
        }

        String learningGoal = submitDTO.getLearningGoal();
        if (learningGoal != null && !learningGoal.isBlank()) {
            learningGoal = learningGoal.trim();
            if (!ALLOWED_GOALS.contains(learningGoal)) {
                throw new IllegalArgumentException("learningGoal 非法");
            }
        } else {
            learningGoal = null;
        }

        List<Tag> tags = tagMapper.selectOnboardingTagsByIds(tagIds);
        if (tags == null || tags.size() != tagIds.size()) {
            throw new IllegalArgumentException("tagIds 包含不存在或不支持的标签");
        }

        saveOrUpdateProfile(userId, submitDTO.getCurrentLevel(), learningGoal);

        userInterestTagMapper.deleteByUserIdAndSource(userId, INIT_SOURCE);

        List<UserInterestTag> rows = tagIds.stream()
                .map(tagId -> {
                    UserInterestTag row = new UserInterestTag();
                    row.setUserId(userId);
                    row.setTagId(tagId);
                    row.setWeight(1.0);
                    row.setSource(INIT_SOURCE);
                    return row;
                })
                .toList();
        userInterestTagMapper.batchInsert(rows);

        redisTemplate.delete(COLD_START_RECOMMEND_KEY + userId);
    }

    @Override
    public OnboardingStatusVO getStatus(Long userId) {
        UserOnboardingProfile profile = userOnboardingProfileMapper.selectByUserId(userId);

        OnboardingStatusVO vo = new OnboardingStatusVO();
        if (profile == null || profile.getOnboardingStatus() == null || profile.getOnboardingStatus() != 1) {
            vo.setCompleted(false);
            return vo;
        }

        vo.setCompleted(true);
        vo.setCurrentLevel(profile.getCurrentLevel());
        vo.setLearningGoal(profile.getLearningGoal());
        vo.setTagIds(userInterestTagMapper.selectTagIdsByUserIdAndSource(userId, INIT_SOURCE));
        return vo;
    }

    private void saveOrUpdateProfile(Long userId, Integer currentLevel, String learningGoal) {
        UserOnboardingProfile profile = userOnboardingProfileMapper.selectByUserId(userId);
        if (profile == null) {
            UserOnboardingProfile newProfile = new UserOnboardingProfile();
            newProfile.setUserId(userId);
            newProfile.setCurrentLevel(currentLevel);
            newProfile.setLearningGoal(learningGoal);
            newProfile.setOnboardingStatus(1);
            userOnboardingProfileMapper.insert(newProfile);
            return;
        }

        profile.setCurrentLevel(currentLevel);
        profile.setLearningGoal(learningGoal);
        profile.setOnboardingStatus(1);
        userOnboardingProfileMapper.updateById(profile);
    }

    private List<OnboardingLevelOptionVO> buildLevelOptions() {
        return List.of(
                new OnboardingLevelOptionVO(1, "零基础"),
                new OnboardingLevelOptionVO(2, "入门"),
                new OnboardingLevelOptionVO(3, "有基础"));
    }

    private List<OnboardingLearningGoalOptionVO> buildLearningGoalOptions() {
        return List.of(
                new OnboardingLearningGoalOptionVO("JOB", "找工作"),
                new OnboardingLearningGoalOptionVO("PROJECT", "做项目"),
                new OnboardingLearningGoalOptionVO("FOUNDATION", "打基础"),
                new OnboardingLearningGoalOptionVO("EXAM", "备考"));
    }
}
