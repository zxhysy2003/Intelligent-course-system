package com.sy.course_system.common;

public class UserContext {
    private static final ThreadLocal<UserInfo> USER_HOLDER = new ThreadLocal<>();

    public static void set(UserInfo userInfo) {
        USER_HOLDER.set(userInfo);
    }

    public static UserInfo get() {
        return USER_HOLDER.get();
    }

    public static void clear() {
        USER_HOLDER.remove();
    }

    public static Long getUserId() {
        UserInfo userInfo = USER_HOLDER.get();
        return userInfo != null ? userInfo.getUserId() : null;
    }

    public static String getUsername() {
        UserInfo userInfo = USER_HOLDER.get();
        return userInfo != null ? userInfo.getUsername() : null;
    }

    public static String getRole() {
        UserInfo userInfo = USER_HOLDER.get();
        return userInfo != null ? userInfo.getRole() : null;
    }
    
}
