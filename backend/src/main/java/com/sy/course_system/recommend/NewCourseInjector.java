package com.sy.course_system.recommend;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;

/**
 * 新课注入组件：在常规推荐结果中按固定插槽插入新课候选，保证冷启动新课有稳定曝光。
 *
 * 策略：
 * - 使用固定插槽保证新课稳定曝光位置（默认 [2, 7, 12]）；
 * - 超出插槽后追加在尾部，避免复杂重排；
 * - 按 courseId 去重，避免同一课程在 CF 与新课候选重复出现；
 * - 注入数量受 {@code inject-limit} 和 {@code max-exposure-ratio} 双重约束。
 */
@Component
public class NewCourseInjector {

    private final RecommendProperties recommendProperties;

    public NewCourseInjector(RecommendProperties recommendProperties) {
        this.recommendProperties = recommendProperties;
    }

    public boolean isEnabled() {
        return recommendProperties.getNewCourse().isEnabled();
    }

    /**
     * 计算本次结果可注入的新课上限。
     */
    public int calculateInjectLimit(int regularItemSize) {
        RecommendProperties.NewCourse config = recommendProperties.getNewCourse();
        if (!config.isEnabled()) {
            return 0;
        }
        int safeInjectLimit = Math.max(config.getInjectLimit(), 0);
        if (safeInjectLimit == 0 || regularItemSize <= 0) {
            return 0;
        }
        double safeExposureRatio = Math.max(config.getMaxExposureRatio(), 0.0);
        int byRatio = (int) Math.floor(regularItemSize * safeExposureRatio);
        if (byRatio <= 0 && safeExposureRatio > 0) {
            byRatio = 1;
        }
        return Math.min(safeInjectLimit, byRatio);
    }

    /**
     * 将新课候选插入常规推荐结果。
     */
    public List<HybridRecommendItemDTO> merge(List<HybridRecommendItemDTO> regularItems,
            List<HybridRecommendItemDTO> newCourseCandidates, int injectLimit) {
        if (regularItems == null || regularItems.isEmpty()) {
            return regularItems == null ? List.of() : regularItems;
        }
        if (newCourseCandidates == null || newCourseCandidates.isEmpty() || injectLimit <= 0) {
            return regularItems;
        }

        List<HybridRecommendItemDTO> merged = new ArrayList<>(regularItems);
        Set<Long> seenCourseIds = merged.stream()
                .map(HybridRecommendItemDTO::getCourseId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Integer> injectSlots = recommendProperties.getNewCourse().getInjectSlots();

        int injected = 0;
        for (HybridRecommendItemDTO candidate : newCourseCandidates) {
            Long courseId = candidate.getCourseId();
            if (courseId == null || !seenCourseIds.add(courseId)) {
                continue;
            }
            int targetIndex;
            if (injectSlots != null && injected < injectSlots.size()
                    && injectSlots.get(injected) != null
                    && injectSlots.get(injected) >= 0
                    && injectSlots.get(injected) < merged.size()) {
                targetIndex = injectSlots.get(injected);
            } else {
                targetIndex = merged.size();
            }
            merged.add(targetIndex, candidate);
            injected++;
            if (injected >= injectLimit) {
                break;
            }
        }
        return merged;
    }
}
