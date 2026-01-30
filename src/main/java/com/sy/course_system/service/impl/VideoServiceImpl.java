package com.sy.course_system.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sy.course_system.entity.Video;
import com.sy.course_system.mapper.VideoMapper;
import com.sy.course_system.service.VideoService;

@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    @Override
    public Integer getVideoDurationInSeconds(Long courseId) {
        LambdaQueryWrapper <Video> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Video::getCourseId, courseId);
        Video video = this.getOne(queryWrapper);
        if (video != null) {
            return video.getDurationSeconds();
        }
        return null;
    }

    @Override
    public String getVideoPath(Long courseId) {
        LambdaQueryWrapper <Video> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Video::getCourseId, courseId);
        Video video = this.getOne(queryWrapper);
        if (video != null) {
            return video.getVideoPath();
        }
        return null;
    }
    
}
