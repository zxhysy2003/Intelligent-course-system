package com.sy.course_system.dto;

import com.sy.course_system.enums.UserType;

public class UserRoleUpdateDTO {
    private UserType role;

    public UserType getRole() {
        return role;
    }

    public void setRole(UserType role) {
        this.role = role;
    }
}
