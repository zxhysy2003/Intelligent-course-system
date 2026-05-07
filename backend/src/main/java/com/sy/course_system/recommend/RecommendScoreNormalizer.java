package com.sy.course_system.recommend;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sy.course_system.config.RecommendProperties;
import com.sy.course_system.dto.recommend.HybridRecommendItemDTO;
import com.sy.course_system.dto.recommend.HybridRecommendResponseDTO;

/**
 * 展示分归一化组件：将不同推荐来源的内部打分统一映射到同一展示区间。
 *
 * 这里刻意把“后端排序分”和“前端展示分”拆开：
 * - finalScore 继续服务排序，不要求不同推荐来源天然同分布；
 * - recommendScore 只服务前端展示，统一落在中高位区间，表达相对推荐度。
 *
 * 统一公式分两步：
 * 1) 先按推荐来源把内部信号压缩成 normalized，范围期望为 0~1；
 * 2) 再计算 round(base + normalized * span)，默认 0 -> 60，1 -> 95。
 *
 * 各来源 normalized 规则：
 * - CF / 新课冷启动：finalScore 已按 0~1 设计，直接裁剪映射；
 * - 冷启动用户：finalScore 是启发式原始分，可能不在 0~1，用指数函数平滑压缩；
 * - 热门兜底：没有可比较的模型分，按最终列表位置给稳定梯度；
 * - 未知来源：走 clamp01 兜底，避免新增来源把异常值透传到前端。
 */
@Component
public class RecommendScoreNormalizer {

    @Autowired
    private RecommendProperties recommendProperties;

    /**
     * 给整批推荐结果补齐展示分。
     *
     * 该方法会在写缓存和读缓存时都调用：
     * - 写缓存前调用，保证本次即时返回对象已经带 recommendScore；
     * - 读缓存后调用，兼容历史缓存缺字段或展示分规则后续调整。
     */
    public void fillRecommendScores(HybridRecommendResponseDTO response) {
        if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
            return;
        }
        List<HybridRecommendItemDTO> items = response.getItems();
        for (int i = 0; i < items.size(); i++) {
            HybridRecommendItemDTO item = items.get(i);
            if (item == null) {
                continue;
            }
            item.setRecommendScore(toRecommendScore(item, i));
        }
    }

    /**
     * 将单条推荐项换算成前端展示分。
     *
     * 注意：这里不会反向影响 finalScore，也不会改变推荐排序；它只是把不同来源的内部信号
     * 转成同一展示口径的整数分。
     */
    public int toRecommendScore(HybridRecommendItemDTO item, int index) {
        double normalized = normalize(item, index);
        if (!Double.isFinite(normalized) || normalized < 0.0) {
            normalized = 0.0;
        } else if (normalized > 1.0) {
            normalized = 1.0;
        }
        RecommendProperties.Score scoreConfig = recommendProperties.getScore();
        return (int) Math.round(scoreConfig.getBase() + normalized * scoreConfig.getSpan());
    }

    /**
     * 按推荐来源把内部信号压缩到 0~1 附近。
     *
     * 热门兜底优先使用列表位置，因为它的 finalScore 固定为 0，没有可比较的模型分。
     * 冷启动用户使用 1 - exp(-score / scale)，这个函数单调递增且天然小于 1，
     * 可以保留相对大小，同时避免启发式高分把展示分直接顶到上限。
     * 其他来源默认认为 finalScore 已经是 0~1 语义，再通过 clamp01 做边界保护。
     */
    private double normalize(HybridRecommendItemDTO item, int index) {
        String source = item.getRecommendSource();
        if (RecommendSource.HOT_FALLBACK.code().equals(source)) {
            RecommendProperties.Score scoreConfig = recommendProperties.getScore();
            return Math.max(scoreConfig.getHotFallbackMin(),
                    scoreConfig.getHotFallbackBase() - index * scoreConfig.getHotFallbackStep());
        }

        double finalScore = safeFinalScore(item.getFinalScore());
        if (RecommendSource.COLD_START_USER.code().equals(source)) {
            double scale = Math.max(recommendProperties.getScore().getColdStartUserScale(), 1e-9);
            return 1.0 - Math.exp(-finalScore / scale);
        }
        return clamp01(finalScore);
    }

    /**
     * 清洗 finalScore 输入。
     *
     * null、NaN、Infinity 和负数都没有稳定展示语义，统一降为 0，对应展示分下限 60。
     */
    private double safeFinalScore(Double finalScore) {
        if (finalScore == null || !Double.isFinite(finalScore) || finalScore < 0.0) {
            return 0.0;
        }
        return finalScore;
    }

    /**
     * 最后一层 0~1 边界保护。
     *
     * 这让展示分组件对未来新增来源更稳：即使上游误传了 >1 或非有限值，也不会把异常分数暴露给前端。
     */
    private double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
