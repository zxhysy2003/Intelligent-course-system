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
import org.springframework.stereotype.Service;

import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.recommend.CourseReadinessDTO;
import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.dto.recommend.NewCourseBaseCandidateDTO;
import com.sy.course_system.dto.recommend.NewCourseStatDTO;
import com.sy.course_system.dto.recommend.NewCourseTagRowDTO;
import com.sy.course_system.entity.UserOnboardingProfile;
import com.sy.course_system.mapper.CourseMapper;
import com.sy.course_system.mapper.UserInterestTagMapper;
import com.sy.course_system.mapper.UserOnboardingProfileMapper;
import com.sy.course_system.recommend.RecommendSource;
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

    @Autowired
    private CourseMapper courseMapper;
    @Autowired
    private UserInterestTagMapper userInterestTagMapper;
    @Autowired
    private UserOnboardingProfileMapper userOnboardingProfileMapper;
    @Autowired
    private CourseGraphRepository courseGraphRepository;
    @Autowired
    private RecommendProperties recommendProperties;

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

        RecommendProperties.NewCourse config = recommendProperties.getNewCourse();
        int safeLimit = normalizeLimit(limit);
        int safeWindowDays = Math.max(config.getWindowDays(), 1);
        LocalDateTime publishedAfter = LocalDateTime.now().minusDays(safeWindowDays);

        // 这里使用 max(candidateLimit, safeLimit) 是有意设计：
        // 1) safeLimit 表示“最终返回上限”；
        // 2) candidateLimit 表示“预召回候选池深度”；
        // 3) 预召回通常应 >= 最终返回条数，否则经过质量门槛过滤后可能不够返回。
        // 因此默认配置下常取 candidateLimit（如 80），并不是 bug。
        List<NewCourseBaseCandidateDTO> baseCourses = courseMapper.selectOnlineNewCourseBaseCandidates(
                publishedAfter,
                Math.max(Math.max(config.getCandidateLimit(), 0), safeLimit));
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

        Map<Long, CourseTagSnapshot> courseTagsMap = buildCourseTagMap(courseIds);
        Map<Long, Integer> kpCountMap = toCountMap(courseMapper.selectCourseKpCountsByCourseIds(courseIds));
        Map<Long, Integer> learnerCountMap = toCountMap(courseMapper.selectCourseLearnerCountsByCourseIds(courseIds));

        // readiness 来自 Neo4j，成本高于本地质量门槛；先用已批量取回的 MySQL 统计过滤，
        // 避免明显不合格的新课继续触发用户画像和图谱查询。
        List<QualityPassedCourse> qualityPassedCourses = new ArrayList<>();
        for (NewCourseBaseCandidateDTO base : baseCourses) {
            Long courseId = base.getCourseId();
            if (courseId == null) {
                continue;
            }
            CourseTagSnapshot tagSnapshot = courseTagsMap.getOrDefault(courseId, CourseTagSnapshot.empty());
            int kpCount = kpCountMap.getOrDefault(courseId, 0);
            int learnerCount = learnerCountMap.getOrDefault(courseId, 0);
            int duration = base.getDuration() == null ? 0 : base.getDuration();
            if (!passesQualityGate(tagSnapshot.tagCount(), kpCount, duration, learnerCount, config)) {
                continue;
            }
            qualityPassedCourses.add(new QualityPassedCourse(base, tagSnapshot, kpCount, learnerCount, duration));
        }
        if (qualityPassedCourses.isEmpty()) {
            // 质量门槛不依赖用户画像或 readiness；全部不合格时直接结束，保持“无可推荐新课”的语义。
            return List.of();
        }

        Set<Long> userTagIds = new LinkedHashSet<>(
                safeList(userInterestTagMapper.selectTagIdsByUserIdAndSource(userId, INIT_SOURCE)));
        // 常规推荐里的新课候选也会受到 onboarding learningGoal 影响，但这里只做“分数接近时的轻量偏置”，
        // 不把 learningGoal 当成硬过滤条件，避免新课池被压缩成单一类型课程。
        UserOnboardingProfile profile = userOnboardingProfileMapper.selectByUserId(userId);
        String learningGoal = profile == null ? null : profile.getLearningGoal();
        List<Long> qualityPassedCourseIds = qualityPassedCourses.stream()
                .map(course -> course.base().getCourseId())
                .distinct()
                .toList();
        // readiness 只对最终会参与打分的新课有意义；缺失结果仍在打分处按既有规则回退为 1.0。
        Map<Long, Double> readinessMap = loadReadinessMap(userId, qualityPassedCourseIds, config);

        List<ScoredNewCourse> scoredCourses = new ArrayList<>();
        for (QualityPassedCourse candidate : qualityPassedCourses) {
            NewCourseBaseCandidateDTO base = candidate.base();
            Long courseId = base.getCourseId();
            CourseTagSnapshot tagSnapshot = candidate.tagSnapshot();

            List<String> matchedTagNames = tagSnapshot.matchedTagNames(userTagIds);

            double tagMatch = tagSnapshot.calcTagMatch(userTagIds);
            long daysSincePublish = calcDaysSincePublish(base.getPublishTime(), safeWindowDays);
            double freshness = calcFreshness(daysSincePublish, safeWindowDays);
            double quality = calcQuality(candidate.kpCount(), candidate.duration(), config);
            double readiness = readinessMap.getOrDefault(courseId, 1.0);
            boolean goalFit = LearningGoalRuleSupport.isGoalFit(
                    learningGoal,
                    base.getDifficulty(),
                    base.getTitle(),
                    tagSnapshot.tagNames());
            double finalScore = calcFinalScore(tagMatch, freshness, quality, readiness, goalFit, config);

            HybridRecommendItemDTO item = new HybridRecommendItemDTO();
            item.setCourseId(courseId);
            item.setTitle(base.getTitle());
            item.setCoverUrl(base.getCoverUrl());
            item.setDifficulty(base.getDifficulty());
            item.setReadiness(readiness);
            item.setFinalScore(finalScore);
            item.setReason(buildReason(daysSincePublish, matchedTagNames, readiness, quality, goalFit, learningGoal));
            item.setRecommendSource(RecommendSource.COLD_START_COURSE.code());
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
        RecommendProperties.NewCourse config = recommendProperties.getNewCourse();
        int minLimit = Math.max(config.getMinLimit(), 1);
        int maxLimit = Math.max(config.getMaxLimit(), minLimit);
        if (limit == null) {
            return Math.max(minLimit, Math.min(config.getDefaultLimit(), maxLimit));
        }
        int safe = Math.max(limit, minLimit);
        return Math.min(safe, maxLimit);
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
    private boolean passesQualityGate(int tagCount, int kpCount, int duration, int learnerCount,
            RecommendProperties.NewCourse config) {
        return tagCount >= config.getMinTagCount()
                && kpCount >= config.getMinKpCount()
                && duration >= config.getMinDurationSeconds()
                && learnerCount <= config.getMaxLearners();
    }

    /**
     * 将“课程-标签行”整理为快照，避免后续为同一课程重复扫描标签明细。
     */
    private Map<Long, CourseTagSnapshot> buildCourseTagMap(List<Long> courseIds) {
        List<NewCourseTagRowDTO> rows = courseMapper.selectCourseTagRowsByCourseIds(courseIds);
        Map<Long, CourseTagSnapshot> map = new LinkedHashMap<>();
        for (NewCourseTagRowDTO row : safeList(rows)) {
            if (row.getCourseId() == null) {
                continue;
            }
            map.computeIfAbsent(row.getCourseId(), k -> new CourseTagSnapshot()).add(row);
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
    private Map<Long, Double> loadReadinessMap(Long userId, List<Long> courseIds,
            RecommendProperties.NewCourse config) {
        List<CourseReadinessDTO> readinessList = courseGraphRepository.getCourseReadinessBatch(
                userId,
                courseIds,
                config.getReadinessThreshold());
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
     * 默认按知识点数量与课程时长等权聚合，权重和满分阈值可通过配置调整。
     */
    private double calcQuality(int kpCount, int durationSeconds, RecommendProperties.NewCourse config) {
        double kpFullScoreCount = Math.max(config.getQualityKpFullScoreCount(), 1.0);
        double durationFullScoreSeconds = Math.max(config.getQualityDurationFullScoreSeconds(), 1.0);
        double kpWeight = Math.max(0.0, Math.min(1.0, config.getQualityKpWeight()));
        double kpQuality = Math.min(1.0, kpCount / kpFullScoreCount);
        double durationQuality = Math.min(1.0, durationSeconds / durationFullScoreSeconds);
        return kpWeight * kpQuality + (1 - kpWeight) * durationQuality;
    }

    /**
     * 新课最终排序分的唯一组装入口。
     *
     * 先计算 tag/freshness/quality/readiness 加权基础分，再在同一处处理 learningGoal 轻量 bonus。
     * 后续如果继续增加分数修正因子，应优先收口到这里，避免在候选循环里零散改写 finalScore。
     */
    private double calcFinalScore(double tagMatch, double freshness, double quality, double readiness,
            boolean goalFit, RecommendProperties.NewCourse config) {
        double score = calcWeightedScore(tagMatch, freshness, quality, readiness, config);
        if (goalFit) {
            return Math.min(1.0, score + config.getLearningGoalBonus());
        }
        return score;
    }

    /**
     * 基础加权分：按配置权重加权平均。
     *
     * 注意：
     * - 权重为负会被截断为 0；
     * - 总权重为 0 时返回 0，避免除零并显式暴露配置异常。
     */
    private double calcWeightedScore(double tagMatch, double freshness, double quality, double readiness,
            RecommendProperties.NewCourse config) {
        double safeTagWeight = Math.max(0.0, config.getTagWeight());
        double safeFreshnessWeight = Math.max(0.0, config.getFreshnessWeight());
        double safeQualityWeight = Math.max(0.0, config.getQualityWeight());
        double safeReadinessWeight = Math.max(0.0, config.getReadinessWeight());
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

    /**
     * 空安全列表转换，统一避免 NPE。
     */
    private <T> List<T> safeList(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    /**
     * 保存质量门槛后仍要复用的中间结果，避免后续打分阶段重复从 Map 中组装上下文。
     */
    private record QualityPassedCourse(NewCourseBaseCandidateDTO base,
            CourseTagSnapshot tagSnapshot,
            int kpCount,
            int learnerCount,
            int duration) {
    }

    /**
     * 课程标签快照：一次聚合后同时服务质量门槛、兴趣匹配、推荐理由与 learningGoal 判断。
     */
    private static class CourseTagSnapshot {
        private final Map<Long, String> tagNameMap = new LinkedHashMap<>();
        private final Set<String> tagNames = new LinkedHashSet<>();

        private static CourseTagSnapshot empty() {
            return new CourseTagSnapshot();
        }

        private void add(NewCourseTagRowDTO row) {
            Long tagId = row.getTagId();
            if (tagId == null) {
                return;
            }
            String tagName = row.getTagName();
            String existingName = tagNameMap.get(tagId);
            if (!tagNameMap.containsKey(tagId) || isBlank(existingName)) {
                tagNameMap.put(tagId, tagName);
            }
            if (tagName != null && !tagName.isBlank()) {
                tagNames.add(tagName);
            }
        }

        private int tagCount() {
            return tagNameMap.size();
        }

        private List<String> tagNames() {
            return List.copyOf(tagNames);
        }

        private List<String> matchedTagNames(Set<Long> userTagIds) {
            if (userTagIds == null || userTagIds.isEmpty()) {
                return List.of();
            }
            Set<String> matchedNames = new LinkedHashSet<>();
            for (Map.Entry<Long, String> entry : tagNameMap.entrySet()) {
                String name = entry.getValue();
                if (userTagIds.contains(entry.getKey()) && name != null && !name.isBlank()) {
                    matchedNames.add(name);
                }
            }
            return List.copyOf(matchedNames);
        }

        /**
         * 标签匹配度：命中标签数 / 课程标签总数。
         */
        private double calcTagMatch(Set<Long> userTagIds) {
            if (tagNameMap.isEmpty() || userTagIds == null || userTagIds.isEmpty()) {
                return 0.0;
            }
            long matched = 0L;
            for (Long tagId : tagNameMap.keySet()) {
                if (userTagIds.contains(tagId)) {
                    matched++;
                }
            }
            return matched <= 0 ? 0.0 : matched * 1.0 / tagNameMap.size();
        }

        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
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
