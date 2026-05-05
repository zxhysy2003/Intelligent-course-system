package com.sy.course_system.common.util;

import java.time.Duration;
import java.time.LocalDateTime;

public class TimeDecayUtil {
    /**
     * 时间衰减（最近 7 天权重最高）
     */
    public static double decay(LocalDateTime behaviorTime) {

        if(behaviorTime == null) return 1.0;
        
        long days = Duration.between(behaviorTime, LocalDateTime.now()).toDays();

        if (days <= 7) return 1.0;
        if (days <= 30) return 0.85;
        if (days <= 90) return 0.6;
        if (days <= 180) return 0.4;
        return 0.25;
    }
}
