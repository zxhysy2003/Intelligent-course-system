package com.sy.course_system.enums;

public enum CourseOrderType {
    DEFAULT(0, "按人数排序"), // 按人数排序
    NEW(1, "最新排序"),     // 最新排序
    HOT(2, "最热排序"),     // 最热排序
    PROGRESS(3, "进度排序"), // 进度排序
    SCORE(4, "推荐排序"),   // 推荐排序
    DIFFICULTY(5, "难度排序"); // 难度排序

    private final Integer code;
    private final String description;

    CourseOrderType(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
