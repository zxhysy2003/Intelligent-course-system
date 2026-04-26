package com.sy.course_system.vo;

/**
 * 课程视频上传结果。
 *
 * 这是标准响应视图对象，之前放在 dto/course 包下会模糊“入参 DTO / 出参 VO”的边界，
 * 这里归位到 vo 包，减少命名和目录上的认知负担。
 */
public class CourseVideoUploadVO {
    private Long courseId;
    private String videoPath;
    private String videoUrl;
    private Integer durationSeconds;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }
}
