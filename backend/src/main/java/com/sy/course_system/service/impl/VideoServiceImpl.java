package com.sy.course_system.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sy.course_system.entity.Video;
import com.sy.course_system.mapper.VideoMapper;
import com.sy.course_system.service.VideoService;
import com.sy.course_system.vo.CourseDetailVO;

@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {
    private static final Logger log = LoggerFactory.getLogger(VideoServiceImpl.class);

    // 允许上传的视频扩展名集合（小写，带点）
    private static final Set<String> ALLOWED_EXT = Set.of(".mp4", ".mov", ".m4v", ".webm");

    // 注入视频存储根目录（来自 application.yaml: app.upload.video-dir）
    @Value("${app.upload.video-dir}")
    private String videoDir;

    // ffprobe 可执行文件路径，默认直接使用系统 PATH 中的 ffprobe
    @Value("${app.video.ffprobe-path}")
    private String ffprobePath;

    @Override
    public Integer getVideoDurationInSeconds(Long courseId) {
        // 构造查询条件：按 courseId 查询视频记录
        LambdaQueryWrapper <Video> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Video::getCourseId, courseId);
        // 查询单条记录
        Video video = this.getOne(queryWrapper);
        // 返回视频时长（秒），若不存在则返回 null
        if (video != null) {
            return video.getDurationSeconds();
        }
        return null;
    }

    @Override
    public String getVideoPath(Long courseId) {
        // 构造查询条件：按 courseId 查询视频记录
        LambdaQueryWrapper <Video> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Video::getCourseId, courseId);
        // 查询单条记录
        Video video = this.getOne(queryWrapper);
        // 返回视频相对路径（如：{courseId}/{filename}），若不存在则返回 null
        if (video != null) {
            return video.getVideoPath();
        }
        return null;
    }

    /**
     * 上传视频文件并绑定课程
      * 1) 参数校验：文件不能为空，扩展名必须合法
      * 2) 生成唯一文件名，避免重名覆盖
      * 3) 生成保存路径：videoDir/{courseId}/{filename}
      * 4) 保存文件到磁盘
      * 5) 解析视频总时长（秒），解析失败则回退为 0
      * 6) 查询该课程是否已有视频记录，若无则新建记录，若有则更新路径和时长
      * 7) 返回相对路径，供上层拼接访问 URL
      * 注意：此方法仅处理视频文件的存储和数据库记录的创建/更新，不负责课程的其他业务逻辑（如课程状态更新等），上层调用者需自行处理
     */
    @Override
    public String uploadAndBindCourseVideo(MultipartFile file, CourseDetailVO courseDetailVO) {
        // 1) 参数校验：文件不能为空
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("视频文件不能为空");
        }
        
        // 2) 提取并校验文件扩展名
        String ext = extractExt(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException("仅支持 mp4/mov/m4v/webm 格式");
        }

        // 3) 生成唯一文件名，避免重名覆盖
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;

        // 4) 生成相对路径（以课程 ID 分目录）
        String relativePath = courseDetailVO.getId() + "/" + filename;

        // 5) 生成最终保存路径：videoDir/{courseId}/{filename}
        Path target = Paths.get(videoDir, relativePath);
        try {
            // 创建父目录
            Files.createDirectories(target.getParent());
            // 保存文件到磁盘
            file.transferTo(target.toFile());
        } catch (IOException e) {
            // 文件写入失败，抛出运行时异常
            throw new RuntimeException("视频保存失败", e);
        }

        // 6) 解析视频总时长（秒），解析失败则回退为 0
        Integer durationSeconds = probeDurationSeconds(target);

        // 7) 查询该课程是否已有视频记录
        LambdaQueryWrapper<Video> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Video::getCourseId, courseDetailVO.getId());
        Video video = this.getOne(queryWrapper);

        if (video == null) {
            // 8) 若不存在则新建记录
            video = new Video();
            video.setCourseId(courseDetailVO.getId());
            video.setTitle(courseDetailVO.getTitle());
            video.setVideoPath(relativePath);
            // 时长为空或非法时设置为 0
            video.setDurationSeconds(durationSeconds != null && durationSeconds > 0 ? durationSeconds : 0);
            this.save(video);
        } else {
            // 9) 若存在则更新路径和时长
            video.setVideoPath(relativePath);
            if (durationSeconds != null && durationSeconds > 0) {
                video.setDurationSeconds(durationSeconds);
            }
            this.updateById(video);
        }
        // 返回相对路径，供上层拼接访问 URL
        return relativePath;
    }

    /**
     * 从文件名中提取扩展名（小写，带点）
     * 若文件名无扩展名，则默认返回 ".mp4"
     */
    private String extractExt(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".mp4";
        }
        return filename.substring(filename.lastIndexOf(".")).toLowerCase(Locale.ROOT);
    }

    /**
     * 使用 ffprobe 解析视频时长（秒）。
     *
     * 命令示例：
     * ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 input.mp4
     */
    private Integer probeDurationSeconds(Path videoFile) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                ffprobePath,
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                videoFile.toAbsolutePath().toString());

        try {
            Process process = processBuilder.start();
            String output;
            String error;

            try (InputStream out = process.getInputStream();
                    InputStream err = process.getErrorStream()) {
                output = new String(out.readAllBytes(), StandardCharsets.UTF_8).trim();
                error = new String(err.readAllBytes(), StandardCharsets.UTF_8).trim();
            }

            int exitCode = process.waitFor();
            if (exitCode != 0 || output.isEmpty()) {
                log.warn("ffprobe 解析视频时长失败: exitCode={}, error={}", exitCode, error);
                return 0;
            }

            double duration = Double.parseDouble(output);
            if (duration <= 0) {
                return 0;
            }
            return (int) Math.floor(duration);
        } catch (Exception e) {
            log.warn("调用 ffprobe 失败，视频时长回退为 0，file={}", videoFile, e);
            return 0;
        }
    }
    
}
