package com.sy.course_system.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.sy.course_system.dto.CourseRegisterDTO;
import com.sy.course_system.entity.Course;

@Mapper(componentModel = "spring")
public interface CourseMapperStruct {
    CourseMapperStruct INSTANCE = Mappers.getMapper(CourseMapperStruct.class);

    @Mapping(target = "status", expression = "java(com.sy.course_system.enums.CourseStatus.DRAFT.getCode())") 
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    Course toEntity(CourseRegisterDTO dto);
}
