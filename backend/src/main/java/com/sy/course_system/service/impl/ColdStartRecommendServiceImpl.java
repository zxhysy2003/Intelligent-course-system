package com.sy.course_system.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.sy.course_system.dto.coldstart.ColdStartCourseCandidateDTO;
import com.sy.course_system.entity.UserOnboardingProfile;
import com.sy.course_system.mapper.CourseMapper;
import com.sy.course_system.mapper.UserInterestTagMapper;
import com.sy.course_system.mapper.UserOnboardingProfileMapper;
import com.sy.course_system.recommend.support.LearningGoalRuleSupport;
import com.sy.course_system.service.ColdStartRecommendService;
import com.sy.course_system.vo.ColdStartRecommendItemVO;

@Service
/**
 * 冷启动推荐服务实现。
 *
 * 在用户行为数据较少时，本实现通过两阶段策略保证推荐可用性：
 *
 * 第一阶段：根据 onboarding 初始兴趣标签做匹配推荐；
 *
 * 第二阶段：当匹配结果不足时，使用基础课程策略兜底补齐。
 */
public class ColdStartRecommendServiceImpl implements ColdStartRecommendService {

    /** 默认返回条数。 */
    private static final int DEFAULT_LIMIT = 10;
    /** 返回条数上限，避免单次请求过大。 */
    private static final int MAX_LIMIT = 50;
    /** 兴趣标签来源：onboarding 初始化选择。 */
    private static final String INIT_SOURCE = "INIT";
    /** learningGoal 命中时的小幅加权。 */
    private static final double LEARNING_GOAL_BONUS = 1.5;

    private final UserOnboardingProfileMapper userOnboardingProfileMapper;
    private final UserInterestTagMapper userInterestTagMapper;
    private final CourseMapper courseMapper;

    public ColdStartRecommendServiceImpl(UserOnboardingProfileMapper userOnboardingProfileMapper,
            UserInterestTagMapper userInterestTagMapper,
            CourseMapper courseMapper) {
        this.userOnboardingProfileMapper = userOnboardingProfileMapper;
        this.userInterestTagMapper = userInterestTagMapper;
        this.courseMapper = courseMapper;
    }

    /**
     * 生成冷启动推荐结果。
     *
     * 流程概述：
     *
     * 1. 参数校验与 limit 归一化；
     *
     * 2. 读取用户 onboarding 画像与 INIT 兴趣标签；
     *
     * 3. 拉取课程候选并按课程聚合标签；
     *
     * 4. 先做兴趣匹配推荐，再用兜底策略补齐。
     *
     * @param userId 用户 ID，不能为空
     * @param limit  期望返回条数，会限制在合理范围内
     * @return 推荐结果列表（按分数降序、课程 ID 升序）
     */
    @Override
    public List<ColdStartRecommendItemVO> recommend(Long userId, Integer limit) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        // 统一处理 limit 入参，避免非法值影响后续流程。
        int safeLimit = normalizeLimit(limit);

        // 查询用户 onboarding 信息及其初始兴趣标签（来源为 INIT）。
        UserOnboardingProfile profile = userOnboardingProfileMapper.selectByUserId(userId);
        List<Long> initTagIds = userInterestTagMapper.selectTagIdsByUserIdAndSource(userId, INIT_SOURCE);
        Set<Long> initTagIdSet = initTagIds == null ? Set.of() : new LinkedHashSet<>(initTagIds);

        // 课程候选通常是一门课程对应多行（每行一个标签）。
        List<ColdStartCourseCandidateDTO> rows = courseMapper.selectPublishedCoursesWithTagsForColdStart();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        // 将“课程-标签明细行”聚合为“课程 + 标签集合”，便于后续评分。
        Map<Long, CourseAggregate> courseMap = aggregateCourses(rows);

        // results 存最终结果；selectedCourseIds 用于阶段间去重，避免同一课程重复推荐。
        List<ColdStartRecommendItemVO> results = new ArrayList<>();
        Set<Long> selectedCourseIds = new LinkedHashSet<>();

        // 第一阶段：仅当 onboarding 已完成且用户有初始兴趣标签时，执行兴趣匹配推荐。
        if (isCompletedOnboarding(profile) && !initTagIdSet.isEmpty()) {
            List<ColdStartRecommendItemVO> matched = courseMap.values().stream()
                    .map(course -> buildMatchedRecommendation(course,
                            initTagIdSet,
                            profile.getCurrentLevel(),
                            profile.getLearningGoal()))
                    .filter(item -> item != null)
                    // 先按分数降序，再按课程 ID 升序，保证排序稳定且可预测。
                    .sorted(Comparator.comparing(ColdStartRecommendItemVO::getScore).reversed()
                            .thenComparing(ColdStartRecommendItemVO::getCourseId))
                    .limit(safeLimit)
                    .toList();
            for (ColdStartRecommendItemVO item : matched) {
                // 防御性去重：即使上游数据异常，也保证同一课程只返回一次。
                if (selectedCourseIds.add(item.getCourseId())) {
                    results.add(item);
                }
            }
        }

        // 第二阶段：当匹配结果不足时，用基础课程兜底策略补齐到 safeLimit。
        if (results.size() < safeLimit) {
            List<ColdStartRecommendItemVO> fallback = courseMap.values().stream()
                    .filter(course -> !selectedCourseIds.contains(course.courseId))
                    .map(course -> buildFallbackRecommendation(course, profile))
                    // 兜底也沿用同样排序规则，确保最终输出一致性。
                    .sorted(Comparator.comparing(ColdStartRecommendItemVO::getScore).reversed()
                            .thenComparing(ColdStartRecommendItemVO::getCourseId))
                    .toList();

            for (ColdStartRecommendItemVO item : fallback) {
                if (selectedCourseIds.add(item.getCourseId())) {
                    results.add(item);
                }
                // 达到目标数量后立即停止遍历。
                if (results.size() >= safeLimit) {
                    break;
                }
            }
        }

        return results;
    }

    /**
     * 归一化 limit。
     *
     * 当 limit 为空或小于等于 0 时使用默认值；其余情况限制在最大上限内。
     */
    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 判断用户是否完成 onboarding。
     *
     * 当前约定：onboardingStatus == 1 代表完成。
     */
    private boolean isCompletedOnboarding(UserOnboardingProfile profile) {
        return profile != null
                && profile.getOnboardingStatus() != null
                && profile.getOnboardingStatus() == 1;
    }

    /**
     * 将课程候选明细聚合为按 courseId 分组的课程对象。
     *
     * 输入通常来自 SQL 联表结果，一门课程会因多标签产生多行；
     * 该方法负责将其压缩为“每门课程一条”，并收集标签集合。
     */
    private Map<Long, CourseAggregate> aggregateCourses(List<ColdStartCourseCandidateDTO> rows) {
        Map<Long, CourseAggregate> courseMap = new LinkedHashMap<>();
        for (ColdStartCourseCandidateDTO row : rows) {
            // courseId 为空说明数据不完整，直接跳过避免污染推荐结果。
            if (row.getCourseId() == null) {
                continue;
            }

            // 若该课程首次出现则创建聚合对象，否则复用已有对象继续补充标签。
            CourseAggregate course = courseMap.computeIfAbsent(row.getCourseId(), id -> new CourseAggregate(
                    row.getCourseId(),
                    row.getTitle(),
                    row.getCoverUrl(),
                    row.getDifficulty()));
            if (row.getTagId() != null) {
                // 以 tagId 为键去重，避免同标签重复写入。
                course.tags.put(row.getTagId(), row.getTagName());
            }
        }
        return courseMap;
    }

    /**
     * 构建“兴趣匹配”推荐项。
     *
     * 打分规则：
     *
     * 1. 每命中一个兴趣标签 +10；
     *
     * 2. 课程难度与当前水平匹配 +3；
     *
     * 3. 入门/基础类课程额外 +1。
     */
    private ColdStartRecommendItemVO buildMatchedRecommendation(CourseAggregate course,
            Set<Long> initTagIds,
            Integer currentLevel,
            String learningGoal) {
        List<String> matchedTagNames = findMatchedTagNames(course, initTagIds);
        // 没有命中任何兴趣标签，不属于匹配推荐，返回 null 供上游过滤。
        if (matchedTagNames.isEmpty()) {
            return null;
        }

        double score = matchedTagNames.size() * 10.0;
        boolean difficultyMatched = isDifficultyMatched(currentLevel, course.difficulty);
        if (difficultyMatched) {
            score += 3.0;
        }
        if (isIntroOrTheoryCourse(course)) {
            score += 1.0;
        }
        // learningGoal 只做轻量 bonus，不替代兴趣标签命中本身。
        // 这样即使用户目标很明确，也不会把“完全没命中 INIT 兴趣标签”的课程硬顶到匹配推荐里。
        boolean goalFit = LearningGoalRuleSupport.isGoalFit(
                learningGoal,
                course.difficulty,
                course.title,
                course.tags.values());
        if (goalFit) {
            score += LEARNING_GOAL_BONUS;
        }

        ColdStartRecommendItemVO item = baseItem(course, score);
        item.setReason(buildMatchedReason(matchedTagNames, difficultyMatched, goalFit, learningGoal));
        return item;
    }

    /**
     * 构建“兜底”推荐项。
     *
     * 当兴趣匹配不足时，优先推荐更基础、可入门且难度相对合适的课程。
     *
     * 打分规则：
     *
     * 1. 难度为 1（基础）+6；
     *
     * 2. 标题/标签包含入门或基础特征 +4；
     *
     * 3. 与用户当前水平匹配 +2。
     */
    private ColdStartRecommendItemVO buildFallbackRecommendation(CourseAggregate course,
            UserOnboardingProfile profile) {
        Integer currentLevel = profile == null ? null : profile.getCurrentLevel();
        String learningGoal = profile == null ? null : profile.getLearningGoal();

        double score = 0.0;
        if (course.difficulty != null && course.difficulty == 1) {
            score += 6.0;
        }
        if (isIntroOrTheoryCourse(course)) {
            score += 4.0;
        }
        if (isDifficultyMatched(currentLevel, course.difficulty)) {
            score += 2.0;
        }
        boolean goalFit = LearningGoalRuleSupport.isGoalFit(
                learningGoal,
                course.difficulty,
                course.title,
                course.tags.values());
        if (goalFit) {
            score += LEARNING_GOAL_BONUS;
        }

        ColdStartRecommendItemVO item = baseItem(course, score);
        item.setReason(buildFallbackReason(course, currentLevel, goalFit, learningGoal));
        return item;
    }

    /**
     * 构建推荐项的通用基础字段，避免重复赋值。
     */
    private ColdStartRecommendItemVO baseItem(CourseAggregate course, double score) {
        ColdStartRecommendItemVO item = new ColdStartRecommendItemVO();
        item.setCourseId(course.courseId);
        item.setTitle(course.title);
        item.setCoverUrl(course.coverUrl);
        item.setDifficulty(course.difficulty);
        item.setScore(score);
        return item;
    }

    /**
     * 提取课程命中的兴趣标签名称列表。
     *
     * 会过滤空白标签名，并通过 distinct 做名称去重。
     */
    private List<String> findMatchedTagNames(CourseAggregate course, Set<Long> initTagIds) {
        return course.tags.entrySet().stream()
                .filter(entry -> initTagIds.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }

    /**
     * 判断课程难度是否与用户当前水平匹配。
     *
     * 匹配规则：
     *
     * level=1 -> 仅 difficulty=1；
     *
     * level=2 -> difficulty=1/2；
     *
     * level=3 -> difficulty=2/3。
     */
    private boolean isDifficultyMatched(Integer currentLevel, Integer difficulty) {
        if (currentLevel == null || difficulty == null) {
            return false;
        }
        return switch (currentLevel) {
            case 1 -> difficulty == 1;
            case 2 -> difficulty == 1 || difficulty == 2;
            case 3 -> difficulty == 2 || difficulty == 3;
            default -> false;
        };
    }

    /**
     * 判断课程是否属于入门/理论基础导向。
     *
     * 同时检查标签与标题两个维度，尽量提高识别覆盖率。
     */
    private boolean isIntroOrTheoryCourse(CourseAggregate course) {
        boolean introByTag = course.tags.values().stream()
                .anyMatch(name -> "入门".equals(name) || "计算机基础".equals(name));
        // 这里故意保留一套更窄的本地启发式，而不是直接复用 LearningGoalRuleSupport 里的基础课规则：
        // - 本方法只服务冷启动中的“额外 +1”补偿；
        // - 若直接放宽到“原理”等更广泛词表，会扩大这条弱加分的覆盖面，改变现有冷启动排序。
        boolean introByTitle = course.title != null
                && !course.title.isBlank()
                && (course.title.contains("入门") || course.title.contains("基础"));
        return introByTag || introByTitle;
    }

    /**
     * 生成兴趣匹配推荐的可解释原因文案。
     */
    private String buildMatchedReason(List<String> matchedTagNames,
            boolean difficultyMatched,
            boolean goalFit,
            String learningGoal) {
        String reason = "匹配兴趣标签：" + String.join("、", matchedTagNames);
        if (difficultyMatched) {
            reason += "；难度适合当前水平";
        }
        if (goalFit) {
            reason += "；符合当前学习目标（" + LearningGoalRuleSupport.goalLabel(learningGoal) + "）";
        }
        return reason;
    }

    /**
     * 生成兜底推荐的可解释原因文案。
     */
    private String buildFallbackReason(CourseAggregate course,
            Integer currentLevel,
            boolean goalFit,
            String learningGoal) {
        List<String> parts = new ArrayList<>();
        parts.add("默认基础课程兜底");
        if (course.tags.values().stream().anyMatch(name -> "入门".equals(name))) {
            parts.add("带有入门标签");
        } else if (course.tags.values().stream().anyMatch(name -> "计算机基础".equals(name))) {
            parts.add("偏计算机基础");
        }
        if (isDifficultyMatched(currentLevel, course.difficulty)) {
            parts.add("难度适合当前水平");
        }
        if (goalFit) {
            parts.add("符合当前学习目标（" + LearningGoalRuleSupport.goalLabel(learningGoal) + "）");
        }
        return String.join("；", parts);
    }

    /**
     * 课程聚合结构。
     *
     * 用于承载一门课程的基础信息和其标签集合，避免后续计算时重复扫描明细行。
     */
    private static class CourseAggregate {
        /** 课程 ID。 */
        private final Long courseId;
        /** 课程标题。 */
        private final String title;
        /** 课程封面地址。 */
        private final String coverUrl;
        /** 课程难度等级。 */
        private final Integer difficulty;
        /** 标签集合：key=tagId，value=tagName。 */
        private final Map<Long, String> tags = new LinkedHashMap<>();

        private CourseAggregate(Long courseId, String title, String coverUrl, Integer difficulty) {
            this.courseId = courseId;
            this.title = title;
            this.coverUrl = coverUrl;
            this.difficulty = difficulty;
        }
    }
}
