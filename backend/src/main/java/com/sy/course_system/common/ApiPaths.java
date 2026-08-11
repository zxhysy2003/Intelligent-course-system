package com.sy.course_system.common;

/**
 * 对外 JSON API 的统一版本路径。
 * 静态视频资源不属于 JSON API，继续使用 /videos/**。
 */
public final class ApiPaths {
    public static final String API_V1 = "/api/v1";
    public static final String AUTH = API_V1 + "/auth";
    public static final String USERS = API_V1 + "/users";
    public static final String COURSES = API_V1 + "/courses";
    public static final String KNOWLEDGE_POINTS = API_V1 + "/knowledge-points";
    public static final String LEARNING_BEHAVIORS = API_V1 + "/learning-behaviors";
    public static final String LEARNING_ANALYTICS = API_V1 + "/learning-analytics";
    public static final String RECOMMENDATIONS = API_V1 + "/recommendations";
    public static final String ONBOARDING = API_V1 + "/onboarding";
    public static final String ASSISTANT = API_V1 + "/assistant";
    public static final String ADMIN = API_V1 + "/admin";
    public static final String ADMIN_COURSES = ADMIN + "/courses";
    public static final String ADMIN_USERS = ADMIN + "/users";

    private ApiPaths() {
    }
}
