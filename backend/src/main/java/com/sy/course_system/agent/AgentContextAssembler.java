package com.sy.course_system.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sy.course_system.config.AgentProperties;
import com.sy.course_system.dto.AbilityRadarDTO;
import com.sy.course_system.dto.ProgressChartDTO;
import com.sy.course_system.dto.RadarIndicatorDTO;
import com.sy.course_system.dto.agent.AgentCourseProgressDTO;
import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;
import com.sy.course_system.entity.Tag;
import com.sy.course_system.mapper.TagMapper;
import com.sy.course_system.mapper.UserCourseRelationMapper;
import com.sy.course_system.service.HybridRecommendService;
import com.sy.course_system.service.LearningAnalysisService;
import com.sy.course_system.service.OnboardingService;
import com.sy.course_system.vo.OnboardingStatusVO;
import com.sy.course_system.vo.agent.AgentSourceVO;

@Component
public class AgentContextAssembler {

    private final OnboardingService onboardingService;
    private final LearningAnalysisService learningAnalysisService;
    private final HybridRecommendService hybridRecommendService;
    private final UserCourseRelationMapper userCourseRelationMapper;
    private final TagMapper tagMapper;
    private final AgentProperties properties;

    public AgentContextAssembler(OnboardingService onboardingService,
            LearningAnalysisService learningAnalysisService,
            HybridRecommendService hybridRecommendService,
            UserCourseRelationMapper userCourseRelationMapper,
            TagMapper tagMapper,
            AgentProperties properties) {
        this.onboardingService = onboardingService;
        this.learningAnalysisService = learningAnalysisService;
        this.hybridRecommendService = hybridRecommendService;
        this.userCourseRelationMapper = userCourseRelationMapper;
        this.tagMapper = tagMapper;
        this.properties = properties;
    }

    public AgentContextSnapshot assemble(Long userId) {
        List<AgentSourceVO> sources = new ArrayList<>();
        List<String> sections = new ArrayList<>();
        List<String> fallbackLines = new ArrayList<>();

        OnboardingStatusVO onboarding = safeGet(() -> onboardingService.getStatus(userId));
        if (onboarding != null) {
            List<String> tagNames = listTagNames(onboarding.getTagIds());
            sections.add("""
                    【用户学习画像】
                    - 引导完成：%s
                    - 当前基础：%s
                    - 学习目标：%s
                    - 兴趣标签：%s
                    """.formatted(
                    Boolean.TRUE.equals(onboarding.getCompleted()) ? "已完成" : "未完成",
                    levelText(onboarding.getCurrentLevel()),
                    valueOrUnknown(onboarding.getLearningGoal()),
                    tagNames.isEmpty() ? "暂无" : String.join("、", tagNames)));
            fallbackLines.add("学习画像：" + levelText(onboarding.getCurrentLevel()) + "，目标 "
                    + valueOrUnknown(onboarding.getLearningGoal()) + "。");
            sources.add(new AgentSourceVO("profile", "学习画像", "当前基础、学习目标和兴趣标签", "onboarding"));
        }

        ProgressChartDTO progress = safeGet(() -> learningAnalysisService.getProgressChart(userId, 30));
        if (progress != null) {
            sections.add("""
                    【近 30 天学习进度】
                    - 已选课程：%s
                    - 完成课程：%s
                    - 平均进度：%s%%
                    - 累计学习时长：%s 分钟
                    """.formatted(
                    numberText(progress.getTotalCourses()),
                    numberText(progress.getFinishedCourses()),
                    decimalText(progress.getAvgProgress()),
                    Math.round(nullToZero(progress.getTotalLearnedSeconds()) / 60.0d)));
            fallbackLines.add("进度概览：已选 " + numberText(progress.getTotalCourses()) + " 门，完成 "
                    + numberText(progress.getFinishedCourses()) + " 门，平均进度 "
                    + decimalText(progress.getAvgProgress()) + "%。");
            sources.add(new AgentSourceVO("progress", "学习进度", "近 30 天进度和学习时长汇总", "analysis/progress"));
        }

        AbilityRadarDTO ability = safeGet(() -> learningAnalysisService.getAbilityRadar(userId));
        if (ability != null && ability.getIndicator() != null && ability.getValues() != null) {
            String abilitySummary = abilitySummary(ability);
            sections.add("【能力雷达】\n" + abilitySummary);
            fallbackLines.add("能力雷达：" + abilitySummary.replace("\n", "；"));
            sources.add(new AgentSourceVO("ability", "能力雷达", "知识维度掌握度估计", "analysis/ability-radar"));
        }

        List<AgentCourseProgressDTO> courses = safeList(() -> userCourseRelationMapper.selectRecentCourseProgress(
                userId, Math.max(1, properties.maxContextCourses())));
        if (!courses.isEmpty()) {
            sections.add("【最近学习课程】\n" + courses.stream()
                    .map(this::courseProgressLine)
                    .collect(Collectors.joining("\n")));
            fallbackLines.add("最近学习：" + courses.stream()
                    .limit(3)
                    .map(AgentCourseProgressDTO::getTitle)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("、")) + "。");
            sources.add(new AgentSourceVO("course-progress", "最近学习课程", "当前用户已选课程和进度", "user_course_relation"));
        }

        List<HybridRecommendItemDTO> recommendations = safeGetRecommendations(userId);
        if (!recommendations.isEmpty()) {
            sections.add("【推荐候选 Top " + recommendations.size() + "】\n" + recommendations.stream()
                    .map(this::recommendLine)
                    .collect(Collectors.joining("\n")));
            fallbackLines.add("优先推荐：" + recommendations.stream()
                    .limit(3)
                    .map(HybridRecommendItemDTO::getTitle)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("、")) + "。");
            sources.add(new AgentSourceVO("recommendation", "混合推荐", "推荐课程、推荐原因和学习准备度", "recommend/hybrid"));
        }

        String systemPrompt = """
                你是智能课程系统中的学生学习助手。请使用中文，基于下方系统上下文回答学生问题。

                约束：
                1. 只提供学习建议、课程选择建议、推荐解释、薄弱点分析和学习路径建议。
                2. 不承诺替用户选课、收藏、删除、修改进度或执行任何写操作。
                3. 不编造系统中没有的课程、分数、知识点或用户行为。
                4. 如果上下文不足，明确说明缺少哪些数据，并给出可执行的下一步查看建议。
                5. 回答要具体，优先引用课程名、推荐原因、准备度、学习进度和薄弱维度。

                系统上下文：
                %s
                """.formatted(sections.isEmpty() ? "暂无可用学习上下文。" : String.join("\n", sections));

        String fallbackSummary = fallbackLines.isEmpty()
                ? "系统暂时没有读取到完整学习画像，可以先从课程推荐页和学习进度页补充信息。"
                : String.join("\n", fallbackLines);
        return new AgentContextSnapshot(systemPrompt, fallbackSummary, sources);
    }

    private List<String> listTagNames(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return safeList(() -> tagMapper.selectOnboardingTagsByIds(tagIds)).stream()
                .map(Tag::getName)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<HybridRecommendItemDTO> safeGetRecommendations(Long userId) {
        HybridRecommendResponseDTO response = safeGet(() -> hybridRecommendService.recommend(userId));
        if (response == null || response.getItems() == null) {
            return List.of();
        }
        return response.getItems().stream()
                .filter(item -> item.getCourseId() != null)
                .limit(Math.max(1, properties.maxContextCourses()))
                .toList();
    }

    private String courseProgressLine(AgentCourseProgressDTO course) {
        return "- %s：难度 %s，进度 %s%%，状态 %s，已学 %s 分钟%s".formatted(
                valueOrUnknown(course.getTitle()),
                difficultyText(course.getDifficulty()),
                numberText(course.getProgress()),
                courseStatusText(course.getStatus()),
                Math.round(nullToZero(course.getLearnedSeconds()) / 60.0d),
                Integer.valueOf(1).equals(course.getFavorite()) ? "，已收藏" : "");
    }

    private String recommendLine(HybridRecommendItemDTO item) {
        return "- %s：推荐分 %s，来源 %s，准备度 %s，理由：%s".formatted(
                valueOrUnknown(item.getTitle()),
                numberText(item.getRecommendScore()),
                valueOrUnknown(item.getRecommendSource()),
                item.getReadiness() == null ? "未知" : Math.round(item.getReadiness() * 100) + "%",
                valueOrUnknown(item.getReason()));
    }

    private String abilitySummary(AbilityRadarDTO ability) {
        List<RadarIndicatorDTO> indicators = ability.getIndicator();
        List<Double> values = ability.getValues();
        int size = Math.min(indicators.size(), values.size());
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String name = indicators.get(i) == null ? "未知维度" : indicators.get(i).getName();
            Double value = values.get(i);
            lines.add("- " + valueOrUnknown(name) + "：" + decimalText(value));
        }
        if (size > 0) {
            int weakestIndex = java.util.stream.IntStream.range(0, size)
                    .boxed()
                    .min(Comparator.comparing(i -> values.get(i) == null ? Double.MAX_VALUE : values.get(i)))
                    .orElse(0);
            lines.add("薄弱维度：" + valueOrUnknown(indicators.get(weakestIndex).getName()));
        }
        return String.join("\n", lines);
    }

    private String levelText(Integer level) {
        if (level == null) {
            return "未知";
        }
        return switch (level) {
            case 1 -> "零基础";
            case 2 -> "入门";
            case 3 -> "有基础";
            default -> "未知";
        };
    }

    private String difficultyText(Integer difficulty) {
        if (difficulty == null) {
            return "未知";
        }
        return switch (difficulty) {
            case 1 -> "初级";
            case 2 -> "中级";
            case 3 -> "高级";
            default -> "未知";
        };
    }

    private String courseStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "未开始";
            case 1 -> "学习中";
            case 2 -> "已完成";
            default -> "未知";
        };
    }

    private String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }

    private String numberText(Number value) {
        return value == null ? "0" : String.valueOf(value);
    }

    private String decimalText(Number value) {
        return value == null ? "0" : String.format("%.1f", value.doubleValue());
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private <T> T safeGet(SupplierWithException<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception ignored) {
            return null;
        }
    }

    private <T> List<T> safeList(SupplierWithException<List<T>> supplier) {
        List<T> result = safeGet(supplier);
        return result == null ? List.of() : result;
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }
}
