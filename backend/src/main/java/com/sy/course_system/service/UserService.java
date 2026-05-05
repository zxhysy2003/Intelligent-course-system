package com.sy.course_system.service;
import com.sy.course_system.common.PageResult;
import com.sy.course_system.dto.LoginDTO;
import com.sy.course_system.dto.UserQueryDTO;
import com.sy.course_system.dto.UserRegisterDTO;
import com.sy.course_system.dto.UserUpdateDTO;
import com.sy.course_system.enums.UserType;
import com.sy.course_system.vo.UserDetailVO;
import com.sy.course_system.vo.UserVO;


import java.util.List;

public interface UserService {
    List<UserVO> listUsers();

    UserVO getUserById(Long id);

    Integer register(UserRegisterDTO registerDTO);

    String login(LoginDTO loginDTO);

    PageResult<UserVO> pageForAdmin(UserQueryDTO queryDTO);

    boolean updateUserRole(Long userId, UserType role);

    boolean updateUserStatus(Long userId, Integer status);

    Integer removeUsers(List<Long> userIds);

    UserDetailVO getUserDetailForAdmin(Long userId);

    boolean updateUser(UserUpdateDTO updateDTO);
    
}
