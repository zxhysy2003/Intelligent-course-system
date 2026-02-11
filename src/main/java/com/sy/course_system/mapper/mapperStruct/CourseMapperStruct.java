package com.sy.course_system.mapper.mapperStruct;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.factory.Mappers;

import com.sy.course_system.dto.course.CourseRegisterDTO;
import com.sy.course_system.dto.course.CourseTempDTO;
import com.sy.course_system.entity.Course;
import com.sy.course_system.vo.CourseDetailVO;
import com.sy.course_system.vo.CourseVO;

@Mapper(componentModel = "spring", nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface CourseMapperStruct {
    CourseMapperStruct INSTANCE = Mappers.getMapper(CourseMapperStruct.class);

    @Mapping(target = "status", expression = "java(com.sy.course_system.enums.CourseStatus.DRAFT.getCode())") 
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    Course toEntity(CourseRegisterDTO dto);

    @Mapping(target = "tagList", expression = "java(tempDTO.getTags() != null ? java.util.Arrays.asList(tempDTO.getTags().split(\",\")) : null)")
    @Mapping(target = "score", ignore = true)
    @Mapping(target = "hotScore", ignore = true)
    CourseVO tempToVO(CourseTempDTO tempDTO);

    CourseDetailVO toDetailVO(Course course);

    List<CourseDetailVO> toDetailVOs(List<Course> courses);
}
