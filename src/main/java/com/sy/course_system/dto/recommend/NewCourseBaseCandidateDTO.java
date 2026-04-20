package com.sy.course_system.dto.recommend;

import java.time.LocalDateTime;

/**
 * 新课候选的基础信息 DTO（第一阶段查询结果）。
 *
 * 只承载 course 主表字段，标签/统计信息由后续分层查询补齐。
 */
public class NewCourseBaseCandidateDTO {

    private Long courseId;
    private String title;
    private String coverUrl;
    private Integer difficulty;
    // 课程时长（秒），用于质量门槛和质量评分
    private Integer duration;
    // 首次上线时间（缺失时在 SQL 层回退为 create_time）
    private LocalDateTime publishTime;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }
}
