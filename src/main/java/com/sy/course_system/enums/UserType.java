package com.sy.course_system.enums;

public enum UserType {
    STUDENT,
    TEACHER,
    ADMIN;

    public static UserType from(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role is invalid");
        }
        try {
            return UserType.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("role must be STUDENT/TEACHER/ADMIN");
        }
    }
}
