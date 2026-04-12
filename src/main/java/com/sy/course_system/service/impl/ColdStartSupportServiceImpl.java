package com.sy.course_system.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.service.ColdStartSupportService;

@Service
public class ColdStartSupportServiceImpl implements ColdStartSupportService {

    private static final long COLD_START_BEHAVIOR_THRESHOLD = 3L;

    @Autowired
    private LearningBehaviorMapper learningBehaviorMapper;

    @Override
    public boolean isColdStartUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        Long behaviorCount = learningBehaviorMapper.countByUserId(userId);
        long safeCount = behaviorCount == null ? 0L : behaviorCount;
        return safeCount < COLD_START_BEHAVIOR_THRESHOLD;
    }
}
