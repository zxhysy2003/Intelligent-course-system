package com.sy.course_system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sy.course_system.dto.course.CourseHotScoreDTO;

@Mapper
public interface CourseHotScoreMapper {

    void upsertHotScores(@Param("scores") List<CourseHotScoreDTO> scores);

    void deleteByCourseId(@Param("courseId") Long courseId);

    void deleteByCourseIds(@Param("courseIds") List<Long> courseIds);

    void deleteHotScoresNotIn(@Param("courseIds") List<Long> courseIds);

    void deleteAllHotScores();
}
