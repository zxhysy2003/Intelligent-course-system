package com.sy.course_system.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import com.sy.course_system.dto.UserRegisterDTO;
import com.sy.course_system.entity.User;
import com.sy.course_system.vo.UserDetailVO;
import com.sy.course_system.vo.UserVO;

/**
 * 用户相关对象映射。
 *
 * 这里只处理“纯字段搬运”：
 * - UserRegisterDTO -> User
 * - User -> UserVO / UserDetailVO
 *
 * 带校验、去重、部分字段更新的逻辑仍保留在 service 中手写处理。
 */
@Mapper(componentModel = "spring", nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface UserMapperStruct {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "nickname", ignore = true)
    @Mapping(target = "role", expression = "java(com.sy.course_system.enums.UserType.STUDENT.name())")
    @Mapping(target = "status", constant = "1")
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    User toEntity(UserRegisterDTO dto);

    UserVO toUserVO(User user);

    List<UserVO> toUserVOs(List<User> users);

    UserDetailVO toUserDetailVO(User user);
}
