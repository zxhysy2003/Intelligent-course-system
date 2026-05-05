package com.sy.course_system.vo;

import java.util.List;

import com.sy.course_system.dto.course.TagOptionDTO;

public class OnboardingOptionsVO {

    private List<TagOptionDTO> tags;
    private List<OnboardingLevelOptionVO> levels;
    private List<OnboardingLearningGoalOptionVO> learningGoals;

    public List<TagOptionDTO> getTags() {
        return tags;
    }

    public void setTags(List<TagOptionDTO> tags) {
        this.tags = tags;
    }

    public List<OnboardingLevelOptionVO> getLevels() {
        return levels;
    }

    public void setLevels(List<OnboardingLevelOptionVO> levels) {
        this.levels = levels;
    }

    public List<OnboardingLearningGoalOptionVO> getLearningGoals() {
        return learningGoals;
    }

    public void setLearningGoals(List<OnboardingLearningGoalOptionVO> learningGoals) {
        this.learningGoals = learningGoals;
    }
}
