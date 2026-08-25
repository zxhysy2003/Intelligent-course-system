package com.sy.course_system.experiment;

import org.springframework.boot.SpringApplication;

import com.sy.course_system.CourseSystemApplication;

/**
 * 使用测试类路径启动并发实验后端，实验配置不会被打入正式 JAR。
 */
public final class ConcurrencyExperimentApplication {

    private ConcurrencyExperimentApplication() {
    }

    public static void main(String[] args) {
        SpringApplication.from(CourseSystemApplication::main)
                .with(StudyInsertFaultInjectionConfiguration.class)
                .run(args);
    }
}
