package com.sy.course_system.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.sy.course_system.converter.UserMapperStruct;
import com.sy.course_system.converter.UserMapperStructImpl;
import com.sy.course_system.dto.UserRegisterDTO;
import com.sy.course_system.entity.User;
import com.sy.course_system.mapper.UserCourseRelationMapper;
import com.sy.course_system.mapper.UserMapper;
import com.sy.course_system.repository.UserNodeRepository;
import com.sy.course_system.vo.UserDetailVO;
import com.sy.course_system.vo.UserVO;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserNodeRepository userNodeRepository;
    @Mock
    private UserCourseRelationMapper userCourseRelationMapper;
    @Spy
    private UserMapperStruct userMapperStruct = new UserMapperStructImpl();

    @Spy
    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
    }

    @Test
    void listUsersShouldMapToUserVOs() {
        doReturn(List.of(user(1L, "alice", "Alice", "alice@test.com", "133", "STUDENT", 1)))
                .when(userService).list();

        List<UserVO> result = userService.listUsers();

        assertEquals(1, result.size());
        UserVO item = result.get(0);
        assertEquals(1L, item.getId());
        assertEquals("alice", item.getUsername());
        assertEquals("Alice", item.getNickname());
        assertEquals("alice@test.com", item.getEmail());
        assertEquals("STUDENT", item.getRole());
        assertEquals(1, item.getStatus());
    }

    @Test
    void getUserDetailForAdminShouldMapPhoneField() {
        doReturn(user(2L, "bob", "Bob", "bob@test.com", "188", "ADMIN", 0))
                .when(userService).getById(2L);

        UserDetailVO result = userService.getUserDetailForAdmin(2L);

        assertNotNull(result);
        assertEquals(2L, result.getId());
        assertEquals("bob", result.getUsername());
        assertEquals("Bob", result.getNickname());
        assertEquals("bob@test.com", result.getEmail());
        assertEquals("188", result.getPhone());
        assertEquals("ADMIN", result.getRole());
        assertEquals(0, result.getStatus());
    }

    @Test
    void getUserDetailForAdminShouldReturnNullWhenUserMissing() {
        doReturn(null).when(userService).getById(99L);

        UserDetailVO result = userService.getUserDetailForAdmin(99L);

        assertNull(result);
    }

    @Test
    void registerShouldMapDefaultRoleAndStatusBeforeSave() {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUsername("newbie");
        dto.setPassword("123456");
        dto.setEmail("newbie@test.com");
        dto.setPhone("15500000000");

        doReturn(null).when(userService).getOne(any());
        doAnswer(invocation -> {
            User toSave = invocation.getArgument(0);
            toSave.setId(10L);
            return true;
        }).when(userService).save(any(User.class));

        Integer result = userService.register(dto);

        assertEquals(1, result);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("newbie", saved.getUsername());
        assertEquals("123456", saved.getPassword());
        assertEquals("newbie@test.com", saved.getEmail());
        assertEquals("15500000000", saved.getPhone());
        assertEquals("STUDENT", saved.getRole());
        assertEquals(1, saved.getStatus());
        verify(userNodeRepository).createUser(10L);
    }

    @Test
    void registerShouldReturnMinusOneWhenUsernameExists() {
        doReturn(user(3L, "exists", null, null, null, "STUDENT", 1))
                .when(userService).getOne(any());

        Integer result = userService.register(new UserRegisterDTO());

        assertEquals(-1, result);
        verify(userService, never()).save(any(User.class));
    }

    private User user(Long id, String username, String nickname, String email, String phone, String role,
            Integer status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setEmail(email);
        user.setPhone(phone);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
