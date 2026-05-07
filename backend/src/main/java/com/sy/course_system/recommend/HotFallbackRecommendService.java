package com.sy.course_system.recommend;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.entity.Course;
import com.sy.course_system.service.CourseService;
import com.sy.course_system.service.LearningAnalysisService;

/**
 * 热门课程兜底推荐：当 CF 和新课候选都为空时，从 Redis 热榜补全结果。
 *
 * 分批扫描策略：
 * 1) 从 Redis 热榜读取一段区间；
 * 2) SQL 侧按在线状态过滤课程摘要；
 * 3) 过滤后数量不足则继续扫描下一段；
 * 4) 直到补满、Redis 无更多数据、或达到扫描上限。
 */
@Service
public class HotFallbackRecommendService {

    private final LearningAnalysisService learningAnalysisService;
    private final CourseService courseService;
    private final RecommendProperties recommendProperties;

    public HotFallbackRecommendService(LearningAnalysisService learningAnalysisService,
            CourseService courseService,
            RecommendProperties recommendProperties) {
        this.learningAnalysisService = learningAnalysisService;
        this.courseService = courseService;
        this.recommendProperties = recommendProperties;
    }

    public List<HybridRecommendItemDTO> buildHotFallbackItems() {
        List<HybridRecommendItemDTO> results = new ArrayList<>();
        int limit = Math.max(recommendProperties.getHotFallback().getLimit(), 0);
        int maxScanCount = Math.max(recommendProperties.getHotFallback().getMaxScanCount(), 0);
        if (limit <= 0 || maxScanCount <= 0) {
            return results;
        }

        Set<Long> addedCourseIds = new LinkedHashSet<>();
        int scannedCount = 0;
        while (results.size() < limit && scannedCount < maxScanCount) {
            int batchSize = Math.min(limit, maxScanCount - scannedCount);
            List<Long> hotCourseIds = learningAnalysisService.getHotCoursesByRange(scannedCount, batchSize);
            if (hotCourseIds == null || hotCourseIds.isEmpty()) {
                break;
            }

            Map<Long, Course> courseSummaryMap = courseService.getOnlineRecommendCourseSummaryMapByIds(hotCourseIds);
            for (Long courseId : hotCourseIds) {
                if (courseId == null || !addedCourseIds.add(courseId)) {
                    continue;
                }
                Course course = courseSummaryMap.get(courseId);
                if (course == null) {
                    continue;
                }
                HybridRecommendItemDTO item = new HybridRecommendItemDTO();
                item.setCourseId(courseId);
                item.setTitle(course.getTitle());
                item.setCoverUrl(course.getCoverUrl());
                item.setDifficulty(course.getDifficulty());
                item.setCfScore(null);
                item.setFinalScore(0.0);
                item.setReason("热门课程兜底：近期较多同学在学");
                item.setRecommendSource(RecommendSource.HOT_FALLBACK.code());
                item.setIsNewCourse(Boolean.FALSE);
                results.add(item);
                if (results.size() >= limit) {
                    break;
                }
            }
            scannedCount += hotCourseIds.size();
        }
        return results;
    }
}
