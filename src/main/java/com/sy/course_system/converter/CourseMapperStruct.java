package com.sy.course_system.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.factory.Mappers;

import com.sy.course_system.dto.course.CourseRegisterDTO;
import com.sy.course_system.dto.course.CourseTempDTO;
import com.sy.course_system.entity.Course;
import com.sy.course_system.entity.Knowledge;
import com.sy.course_system.vo.CourseDetailVO;
import com.sy.course_system.vo.CourseUpdateVO;
import com.sy.course_system.vo.CourseVO;
import com.sy.course_system.vo.KnowledgeVO;

@Mapper(componentModel = "spring", nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface CourseMapperStruct {
    CourseMapperStruct INSTANCE = Mappers.getMapper(CourseMapperStruct.class);

    @Mapping(target = "status", expression = "java(com.sy.course_system.enums.CourseStatus.DRAFT.getCode())")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "publishTime", ignore = true)
    Course toEntity(CourseRegisterDTO dto);

    @Mapping(target = "tagList", expression = "java(tempDTO.getTags() != null ? java.util.Arrays.asList(tempDTO.getTags().split(\",\")) : null)")
    @Mapping(target = "score", ignore = true)
    @Mapping(target = "hotScore", ignore = true)
    CourseVO tempToVO(CourseTempDTO tempDTO);

    CourseDetailVO toDetailVO(Course course);

    List<CourseDetailVO> toDetailVOs(List<Course> courses);

    KnowledgeVO toKnowledgeVO(Knowledge knowledge);

    List<KnowledgeVO> toKnowledgeVOs(List<Knowledge> knowledgePoints);

    @Mapping(target = "categoryId", ignore = true)
    @Mapping(target = "options", ignore = true)
    CourseUpdateVO toUpdateVO(Course course);
}
