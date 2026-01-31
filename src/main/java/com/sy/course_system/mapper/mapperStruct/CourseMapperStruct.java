package com.sy.course_system.mapper.mapperStruct;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.sy.course_system.dto.CourseRegisterDTO;
import com.sy.course_system.dto.CourseTempDTO;
import com.sy.course_system.entity.Course;
import com.sy.course_system.vo.CourseDetailVO;
import com.sy.course_system.vo.CourseVO;

@Mapper(componentModel = "spring")
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
}
