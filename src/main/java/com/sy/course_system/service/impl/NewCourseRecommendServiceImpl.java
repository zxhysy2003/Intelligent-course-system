package com.sy.course_system.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.sy.course_system.dto.recommend.CourseReadinessDTO;
import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.dto.recommend.NewCourseBaseCandidateDTO;
import com.sy.course_system.dto.recommend.NewCourseStatDTO;
import com.sy.course_system.dto.recommend.NewCourseTagRowDTO;
import com.sy.course_system.entity.UserOnboardingProfile;
import com.sy.course_system.mapper.CourseMapper;
import com.sy.course_system.mapper.UserInterestTagMapper;
import com.sy.course_system.mapper.UserOnboardingProfileMapper;
import com.sy.course_system.recommend.support.LearningGoalRuleSupport;
import com.sy.course_system.repository.CourseGraphRepository;
import com.sy.course_system.service.NewCourseRecommendService;

/**
 * 新课冷启动推荐实现（仅作用于“常规用户”推荐链路）。
 *
 * 设计约束：
 * 1) 不改动用户冷启动分支，只在常规推荐中提供“新课候选补充”能力。
 * 2) SQL 采用分层查询：先拉课程基础信息，再按 courseIds 补查标签与统计数据，避免大 join。
 * 3) 第一版评分聚焦 tagMatch / freshness / quality，readiness 作为可复用的轻量补充信号。
 *
 * 主流程：
 * 1) 查询时间窗内已上线课程；
 * 2) 批量补齐标签、知识点数量、学习人数；
 * 3) 质量门槛过滤（避免低质量新课过度曝光）；
 * 4) 计算综合分并生成可解释推荐原因；
 * 5) 按分数与发布时间排序后返回。
 */
@Service
public class NewCourseRecommendServiceImpl implements NewCourseRecommendService {

    private static final String INIT_SOURCE = "INIT";
    private static final String SOURCE_COLD_START_COURSE = "COLD_START_COURSE";
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 10;
    private static final double LEARNING_GOAL_BONUS = 0.05;

    @Autowired
    private CourseMapper courseMapper;
    @Autowired
    private UserInterestTagMapper userInterestTagMapper;
    @Autowired
    private UserOnboardingProfileMapper userOnboardingProfileMapper;
    @Autowired
    private CourseGraphRepository courseGraphRepository;

    @Value("${recommend.new-course.window-days:14}")
    private int windowDays;
    @Value("${recommend.new-course.max-learners:20}")
    private int maxLearners;
    @Value("${recommend.new-course.min-tag-count:1}")
    private int minTagCount;
    @Value("${recommend.new-course.min-kp-count:1}")
    private int minKpCount;
    @Value("${recommend.new-course.min-duration-seconds:300}")
    private int minDurationSeconds;
    /**
     * 候选池深度（不是最终返回条数）。
     *
     * 语义说明：
     * - 该值用于“预召回”阶段，目的是先取到足够多的新课候选，给后续质量门槛过滤和打分留出余量；
     * - 最终返回条数仍受 safeLimit 控制（见流末的 .limit(safeLimit)）。
     */
    @Value("${recommend.new-course.candidate-limit:80}")
    private int candidateLimit;

    @Value("${recommend.new-course.tag-weight:0.45}")
    private double tagWeight;
    @Value("${recommend.new-course.freshness-weight:0.30}")
    private double freshnessWeight;
    @Value("${recommend.new-course.quality-weight:0.20}")
    private double qualityWeight;
    @Value("${recommend.new-course.readiness-weight:0.05}")
    private double readinessWeight;
    @Value("${recommend.new-course.readiness-threshold:0.7}")
    private double readinessThreshold;

    /**
     * 为常规用户生成新课候选列表。
     *
     * 说明：
     * - 这里不负责缓存与最终混排，仅输出已打分的新课候选；
     * - userId 必须有效，否则无法读取用户 INIT 兴趣标签；
     * - limit 会被规范到安全区间，避免异常参数导致候选池失控。
     */
    @Override
    public List<HybridRecommendItemDTO> recommendForRegularUser(Long userId, Integer limit) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        int safeLimit = normalizeLimit(limit);
        int safeWindowDays = Math.max(windowDays, 1);
        LocalDateTime publishedAfter = LocalDateTime.now().minusDays(safeWindowDays);

        // 这里使用 max(candidateLimit, safeLimit) 是有意设计：
        // 1) safeLimit 表示“最终返回上限”；
        // 2) candidateLimit 表示“预召回候选池深度”；
        // 3) 预召回通常应 >= 最终返回条数，否则经过质量门槛过滤后可能不够返回。
        // 因此默认配置下常取 candidateLimit（如 80），并不是 bug。
        List<NewCourseBaseCandidateDTO> baseCourses = courseMapper.selectOnlineNewCourseBaseCandidates(
                publishedAfter,
                Math.max(candidateLimit, safeLimit));
        if (baseCourses == null || baseCourses.isEmpty()) {
            return List.of();
        }

        List<Long> courseIds = baseCourses.stream()
                .map(NewCourseBaseCandidateDTO::getCourseId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (courseIds.isEmpty()) {
            return List.of();
        }

        Map<Long, List<NewCourseTagRowDTO>> courseTagsMap = buildCourseTagMap(courseIds);
        Map<Long, Integer> kpCountMap = toCountMap(courseMapper.selectCourseKpCountsByCourseIds(courseIds));
        Map<Long, Integer> learnerCountMap = toCountMap(courseMapper.selectCourseLearnerCountsByCourseIds(courseIds));

        Set<Long> userTagIds = new LinkedHashSet<>(
                safeList(userInterestTagMapper.selectTagIdsByUserIdAndSource(userId, INIT_SOURCE)));
        // 常规推荐里的新课候选也会受到 onboarding learningGoal 影响，但这里只做“分数接近时的轻量偏置”，
        // 不把 learningGoal 当成硬过滤条件，避免新课池被压缩成单一类型课程。
        UserOnboardingProfile profile = userOnboardingProfileMapper.selectByUserId(userId);
        String learningGoal = profile == null ? null : profile.getLearningGoal();
        Map<Long, Double> readinessMap = loadReadinessMap(userId, courseIds);

        List<ScoredNewCourse> scoredCourses = new ArrayList<>();
        for (NewCourseBaseCandidateDTO base : baseCourses) {
            Long courseId = base.getCourseId();
            if (courseId == null) {
                continue;
            }
            List<NewCourseTagRowDTO> tagRows = courseTagsMap.getOrDefault(courseId, List.of());

            int tagCount = (int) tagRows.stream()
                    .map(NewCourseTagRowDTO::getTagId)
                    .filter(id -> id != null)
                    .distinct()
                    .count();
            int kpCount = kpCountMap.getOrDefault(courseId, 0);
            int learnerCount = learnerCountMap.getOrDefault(courseId, 0);
            int duration = base.getDuration() == null ? 0 : base.getDuration();
            if (!passesQualityGate(tagCount, kpCount, duration, learnerCount)) {
                continue;
            }

            Set<Long> courseTagIds = tagRows.stream()
                    .map(NewCourseTagRowDTO::getTagId)
                    .filter(id -> id != null)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<String> matchedTagNames = findMatchedTagNames(tagRows, userTagIds);

            double tagMatch = calcTagMatch(courseTagIds, userTagIds);
            long daysSincePublish = calcDaysSincePublish(base.getPublishTime(), safeWindowDays);
            double freshness = calcFreshness(daysSincePublish, safeWindowDays);
            double quality = calcQuality(kpCount, duration);
            double readiness = readinessMap.getOrDefault(courseId, 1.0);
            double score = calcScore(tagMatch, freshness, quality, readiness);
            boolean goalFit = LearningGoalRuleSupport.isGoalFit(
                    learningGoal,
                    base.getDifficulty(),
                    base.getTitle(),
                    extractTagNames(tagRows));
            if (goalFit) {
                score = Math.min(1.0, score + LEARNING_GOAL_BONUS);
            }

            HybridRecommendItemDTO item = new HybridRecommendItemDTO();
            item.setCourseId(courseId);
            item.setTitle(base.getTitle());
            item.setCoverUrl(base.getCoverUrl());
            item.setDifficulty(base.getDifficulty());
            item.setReadiness(readiness);
            item.setFinalScore(score);
            item.setReason(buildReason(daysSincePublish, matchedTagNames, readiness, quality, goalFit, learningGoal));
            item.setRecommendSource(SOURCE_COLD_START_COURSE);
            item.setIsNewCourse(Boolean.TRUE);
            scoredCourses.add(new ScoredNewCourse(item, base.getPublishTime()));
        }

        return scoredCourses.stream()
                .sorted(Comparator.comparing((ScoredNewCourse c) -> c.item().getFinalScore()).reversed()
                        // 分数相同时优先更“新”的课程；再以 courseId 保证稳定顺序，避免分页抖动
                        .thenComparing(ScoredNewCourse::publishTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(c -> c.item().getCourseId(), Comparator.nullsLast(Comparator.naturalOrder())))
                .map(ScoredNewCourse::item)
                .limit(safeLimit)
                .toList();
    }

    /**
     * 规范返回条数，防止过小或过大的 limit 影响服务稳定性。
     */
    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        int safe = Math.max(limit, MIN_LIMIT);
        return Math.min(safe, MAX_LIMIT);
    }

    /**
     * 质量门槛：保证进入新课候选的课程具备最基础的信息完整度。
     *
     * 过滤维度：
     * - 标签数量：减少“无语义标签”课程；
     * - 知识点数量：减少内容结构不完整课程；
     * - 时长下限：减少明显不完整的视频内容；
     * - 学习人数上限：限制已沉淀课程误入“新课池”。
     */
    private boolean passesQualityGate(int tagCount, int kpCount, int duration, int learnerCount) {
        return tagCount >= minTagCount
                && kpCount >= minKpCount
                && duration >= minDurationSeconds
                && learnerCount <= maxLearners;
    }

    /**
     * 将“课程-标签行”整理为 map，便于后续按课程快速访问标签集合。
     */
    private Map<Long, List<NewCourseTagRowDTO>> buildCourseTagMap(List<Long> courseIds) {
        List<NewCourseTagRowDTO> rows = courseMapper.selectCourseTagRowsByCourseIds(courseIds);
        Map<Long, List<NewCourseTagRowDTO>> map = new LinkedHashMap<>();
        for (NewCourseTagRowDTO row : safeList(rows)) {
            if (row.getCourseId() == null) {
                continue;
            }
            map.computeIfAbsent(row.getCourseId(), k -> new ArrayList<>()).add(row);
        }
        return map;
    }

    /**
     * 将聚合查询结果转为 courseId -> count 的结构。
     */
    private Map<Long, Integer> toCountMap(List<NewCourseStatDTO> rows) {
        Map<Long, Integer> map = new LinkedHashMap<>();
        for (NewCourseStatDTO row : safeList(rows)) {
            if (row.getCourseId() == null) {
                continue;
            }
            map.put(row.getCourseId(), row.getCountValue() == null ? 0 : row.getCountValue());
        }
        return map;
    }

    /**
     * 批量加载课程可学习性（readiness）。
     *
     * 约定：
     * - 若图谱层无结果，则返回空 map，调用方按默认 1.0 处理；
     * - readiness 为 null 的课程同样按 1.0 兜底，避免“无数据即惩罚”。
     */
    private Map<Long, Double> loadReadinessMap(Long userId, List<Long> courseIds) {
        List<CourseReadinessDTO> readinessList = courseGraphRepository.getCourseReadinessBatch(
                userId,
                courseIds,
                readinessThreshold);
        if (readinessList == null || readinessList.isEmpty()) {
            return Map.of();
        }
        return readinessList.stream()
                .filter(r -> r != null && r.getCourseId() != null)
                .collect(Collectors.toMap(
                        CourseReadinessDTO::getCourseId,
                        r -> r.getReadiness() == null ? 1.0 : r.getReadiness(),
                        (a, b) -> a,
                        LinkedHashMap::new));
    }

    /**
     * 提取命中的兴趣标签名称，用于拼接推荐理由。
     */
    private List<String> findMatchedTagNames(List<NewCourseTagRowDTO> tagRows, Set<Long> userTagIds) {
        if (userTagIds.isEmpty()) {
            return List.of();
        }
        return safeList(tagRows).stream()
                .filter(row -> row.getTagId() != null && userTagIds.contains(row.getTagId()))
                .map(NewCourseTagRowDTO::getTagName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }

    /**
     * 标签匹配度：命中标签数 / 课程标签总数。
     *
     * 该定义强调“课程与用户兴趣的贴合比例”，而不是仅看命中绝对数量。
     */
    private double calcTagMatch(Set<Long> courseTagIds, Set<Long> userTagIds) {
        if (courseTagIds == null || courseTagIds.isEmpty() || userTagIds == null || userTagIds.isEmpty()) {
            return 0.0;
        }
        long matched = courseTagIds.stream().filter(userTagIds::contains).count();
        return matched <= 0 ? 0.0 : matched * 1.0 / courseTagIds.size();
    }

    /**
     * 计算“距首次上线天数”。
     *
     * publishTime 缺失时按窗口最大天数处理，等价于 freshness 记为 0，避免把历史课误当新课。
     */
    private long calcDaysSincePublish(LocalDateTime publishTime, int safeWindowDays) {
        if (publishTime == null) {
            return safeWindowDays;
        }
        long days = Duration.between(publishTime, LocalDateTime.now()).toDays();
        return Math.max(days, 0L);
    }

    /**
     * 新鲜度分数：窗口内线性衰减，越新越高。
     */
    private double calcFreshness(long daysSincePublish, int safeWindowDays) {
        if (daysSincePublish >= safeWindowDays) {
            return 0.0;
        }
        return Math.max(0.0, 1.0 - (daysSincePublish * 1.0 / safeWindowDays));
    }

    /**
     * 内容质量分：使用知识点数量与课程时长做简化归一化估计。
     *
     * 第一版按等权聚合，保持策略简单可解释，便于后续替换为更细粒度质量模型。
     */
    private double calcQuality(int kpCount, int durationSeconds) {
        // TODO: 需要进一步解释为什么知识点数量和课程时长被选为质量指标，以及是否考虑引入其他维度（如课程评分、评论数等）来丰富质量评估。
        double kpQuality = Math.min(1.0, kpCount / 4.0);
        // TODO: 需要进一步解释为什么 30 分钟（1800 秒）被选为“优质内容”的:时长阈值，以及是否需要区分课程类型（如实操课可能更长，理论课可能更短）。
        double durationQuality = Math.min(1.0, durationSeconds / 1800.0);
        return 0.5 * kpQuality + 0.5 * durationQuality;
    }

    /**
     * 综合评分：按配置权重加权平均。
     *
     * 注意：
     * - 权重为负会被截断为 0；
     * - 总权重为 0 时返回 0，避免除零并显式暴露配置异常。
     */
    private double calcScore(double tagMatch, double freshness, double quality, double readiness) {
        double safeTagWeight = Math.max(0.0, tagWeight);
        double safeFreshnessWeight = Math.max(0.0, freshnessWeight);
        double safeQualityWeight = Math.max(0.0, qualityWeight);
        double safeReadinessWeight = Math.max(0.0, readinessWeight);
        double totalWeight = safeTagWeight + safeFreshnessWeight + safeQualityWeight + safeReadinessWeight;
        if (totalWeight <= 0) {
            return 0.0;
        }
        return (safeTagWeight * tagMatch
                + safeFreshnessWeight * freshness
                + safeQualityWeight * quality
                + safeReadinessWeight * readiness) / totalWeight;
    }

    /**
     * 构建推荐原因文案，保证每个新课候选具备可解释信息。
     */
    private String buildReason(long daysSincePublish,
            List<String> matchedTagNames,
            double readiness,
            double quality,
            boolean goalFit,
            String learningGoal) {
        List<String> parts = new ArrayList<>();
        parts.add("新课冷启动：上线" + daysSincePublish + "天");
        if (matchedTagNames != null && !matchedTagNames.isEmpty()) {
            parts.add("匹配兴趣标签：" + String.join("、", matchedTagNames));
        }
        parts.add(String.format("可学习性 %.2f", readiness));
        if (quality >= 0.7) {
            parts.add("内容信息较完整");
        }
        if (goalFit) {
            parts.add("符合当前学习目标（" + LearningGoalRuleSupport.goalLabel(learningGoal) + "）");
        }
        return String.join("；", parts);
    }

    private List<String> extractTagNames(List<NewCourseTagRowDTO> tagRows) {
        // 共享 helper 只接受最小领域输入（tagNames），这里做一次 DTO -> 纯字符串列表的适配，
        // 避免 LearningGoalRuleSupport 反向依赖推荐链路内部的 DTO 类型。
        return safeList(tagRows).stream()
                .map(NewCourseTagRowDTO::getTagName)
                .toList();
    }

    /**
     * 空安全列表转换，统一避免 NPE。
     */
    private <T> List<T> safeList(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    /**
     * 新课候选的内部排序载体（仅用于服务内部，不对外暴露）。
     *
     * 设计原因：
     * - 对外返回的 HybridRecommendItemDTO 不包含 publishTime；
     * - 但新课排序需要在 finalScore 相同场景下按发布时间做二级排序。
     * 因此用该 record 在“评分阶段”临时携带发布时间，排序完成后再映射回 DTO。
     */
    private record ScoredNewCourse(HybridRecommendItemDTO item, LocalDateTime publishTime) {
    }
}
