package com.sy.course_system.dto;

import java.util.List;

public class UserDeleteDTO {
    private List<Long> userIds;

    public List<Long> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<Long> userIds) {
        this.userIds = userIds;
    }
}
