package com.sy.course_system.enums;

public enum CourseStatus {
    DRAFT(0, "草稿"),
    ONLINE(1, "已上线"),
    OFFLINE(2, "已下线");

    private final int code;
    private final String desc;

    CourseStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static CourseStatus getCourseStatus(Integer code) {
        if (code == null) {
            return null;
        }
        for (CourseStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown CourseStatus:" + code);
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
