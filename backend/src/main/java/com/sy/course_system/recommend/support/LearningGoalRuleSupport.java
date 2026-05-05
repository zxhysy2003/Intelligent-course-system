package com.sy.course_system.recommend.support;

/**
 * 学习目标规则支持类。
 *
 * 仅承载推荐域内部的纯规则判断与展示文案映射，不依赖 Spring 或具体业务对象。
 */
public final class LearningGoalRuleSupport {

    private LearningGoalRuleSupport() {
    }

    /**
     * 将 learningGoal 转成推荐文案使用的中文标签。
     *
     * 约定说明：
     * - null / blank / 未知值统一回退为“打基础”，仅用于展示文案容错；
     * - 该回退不代表 isGoalFit(...) 也会命中，后者对未知值仍返回 false；
     * - 这里保守兜底的原因是保护历史数据、测试数据或异常回灌数据，避免 reason 文案直接出现空值。
     */
    public static String goalLabel(String learningGoal) {
        if (learningGoal == null || learningGoal.isBlank()) {
            return "打基础";
        }
        return switch (learningGoal) {
            case "FOUNDATION" -> "打基础";
            case "EXAM" -> "备考";
            case "PROJECT" -> "做项目";
            case "JOB" -> "找工作";
            default -> "打基础";
        };
    }

    /**
     * 判断课程是否符合当前学习目标。
     *
     * 设计边界：
     * - 这里只承载“跨推荐链路复用”的目标匹配规则；
     * - 不负责决定加多少 bonus，也不负责拼接具体 reason 文案；
     * - 各推荐 service 只需要把自己的数据模型适配成 difficulty / title / tagNames 三类最小输入。
     */
    public static boolean isGoalFit(String learningGoal, Integer difficulty, String title, Iterable<String> tagNames) {
        if (learningGoal == null || learningGoal.isBlank()) {
            return false;
        }
        return switch (learningGoal) {
            case "FOUNDATION", "EXAM" -> (difficulty != null && difficulty == 1)
                    || isFoundationLike(title, tagNames);
            case "PROJECT", "JOB" -> isProjectLike(title, tagNames);
            default -> false;
        };
    }

    private static boolean isFoundationLike(String title, Iterable<String> tagNames) {
        if (containsAny(title, "入门", "基础", "计算机基础", "原理")) {
            return true;
        }
        if (tagNames == null) {
            return false;
        }
        for (String tagName : tagNames) {
            if (containsAny(tagName, "入门", "基础", "计算机基础", "原理")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProjectLike(String title, Iterable<String> tagNames) {
        if (containsAny(title, "实战")) {
            return true;
        }
        if (tagNames == null) {
            return false;
        }
        for (String tagName : tagNames) {
            if (containsAny(tagName, "实战")) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
