package com.sy.course_system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sy.course_system.common.PageResult;
import com.sy.course_system.common.util.JwtUtil;
import com.sy.course_system.dto.LoginDTO;
import com.sy.course_system.dto.UserQueryDTO;
import com.sy.course_system.dto.UserRegisterDTO;
import com.sy.course_system.dto.UserUpdateDTO;
import com.sy.course_system.entity.User;
import com.sy.course_system.entity.UserCourseRelation;
import com.sy.course_system.enums.UserType;
import com.sy.course_system.mapper.UserCourseRelationMapper;
import com.sy.course_system.mapper.UserMapper;
import com.sy.course_system.converter.UserMapperStruct;
import com.sy.course_system.repository.UserNodeRepository;
import com.sy.course_system.service.UserService;
import com.sy.course_system.vo.UserDetailVO;
import com.sy.course_system.vo.UserVO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private UserNodeRepository userNodeRepository;
    @Autowired
    private UserCourseRelationMapper userCourseRelationMapper;


    @Override
    public List<UserVO> listUsers() {
        List<User> users = this.list();
        return UserMapperStruct.INSTANCE.toUserVOs(users);
    }

    @Override
    public UserVO getUserById(Long id) {
        User user = this.getById(id);
        if (user == null) {
            return null;
        }
        return UserMapperStruct.INSTANCE.toUserVO(user);
    }

    @Override
    @Transactional(transactionManager = "transactionManager")
    public Integer register(UserRegisterDTO registerDTO) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", registerDTO.getUsername());

        if (this.getOne(queryWrapper) != null) {
            return -1; // 用户名已存在
        }
        User user = UserMapperStruct.INSTANCE.toEntity(registerDTO);
        this.save(user);

        Long userId = user.getId();
        // 在图数据库中创建用户节点
        userNodeRepository.createUser(userId);

        return 1;
    }

    @Override
    public String login(LoginDTO loginDTO) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", loginDTO.getUsername());
        User user = this.getOne(queryWrapper);
        if (user == null) {
            return null; // 用户不存在
        }
        if (!user.getPassword().equals(loginDTO.getPassword())) {
            return null; // 密码错误
        }
        
        // 生成简单的token（实际应用中应使用更安全的方式）
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("role", user.getRole());

        return JwtUtil.generateToken(claims);
    }

    @Override
    public PageResult<UserVO> pageForAdmin(UserQueryDTO queryDTO) {
        int page = queryDTO != null && queryDTO.getPage() != null && queryDTO.getPage() > 0 ? queryDTO.getPage() : 1;
        int pageSize = queryDTO != null && queryDTO.getPageSize() != null && queryDTO.getPageSize() > 0
                ? queryDTO.getPageSize()
                : 10;
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();

        if (queryDTO != null && queryDTO.getKeyword() != null && !queryDTO.getKeyword().isBlank()) {
            String keyword = queryDTO.getKeyword().trim();
            queryWrapper.and(w -> w.like("username", keyword).or().like("email", keyword));
        }
        if (queryDTO != null && queryDTO.getRole() != null) {
            queryWrapper.eq("role", queryDTO.getRole().name());
        }
        if (queryDTO != null && queryDTO.getStatus() != null) {
            queryWrapper.eq("status", queryDTO.getStatus());
        }
        queryWrapper.orderByDesc("id");

        Page<User> pageObj = this.page(new Page<>(page, pageSize), queryWrapper);
        List<UserVO> records = UserMapperStruct.INSTANCE.toUserVOs(pageObj.getRecords());
        return PageResult.of(pageObj.getTotal(), page, pageSize, records);
    }

    @Override
    public boolean updateUserRole(Long userId, UserType role) {
        if (userId == null || role == null) {
            throw new IllegalArgumentException("userId or role is invalid");
        }
        User user = this.getById(userId);
        if (user == null) {
            return false;
        }
        user.setRole(role.name());
        return this.updateById(user);
    }

    @Override
    public boolean updateUserStatus(Long userId, Integer status) {
        if (userId == null || status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("status must be 0 or 1");
        }
        User user = this.getById(userId);
        if (user == null) {
            return false;
        }
        user.setStatus(status);
        return this.updateById(user);
    }

    @Override
    @Transactional(transactionManager = "transactionManager")
    public Integer removeUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        List<User> users = this.listByIds(userIds);
        if (users == null || users.isEmpty()) {
            return -1;
        }
        List<Long> existingIds = users.stream().map(User::getId).collect(Collectors.toList());

        // 1) 删除用户课程关系
        userCourseRelationMapper.delete(
                new LambdaQueryWrapper<UserCourseRelation>().in(UserCourseRelation::getUserId, existingIds));

        // 2) 逻辑删除用户
        boolean removed = this.removeByIds(existingIds);
        if (!removed) {
            return 0;
        }

        // 3) 删除 Neo4j 用户节点（含关系）
        userNodeRepository.deleteUsers(existingIds);
        return existingIds.size();
    }

    @Override
    public UserDetailVO getUserDetailForAdmin(Long userId) {
        User user = this.getById(userId);
        if (user == null) {
            return null;
        }
        return UserMapperStruct.INSTANCE.toUserDetailVO(user);
    }

    @Override
    @Transactional(transactionManager = "transactionManager")
    public boolean updateUser(UserUpdateDTO updateDTO) {
        if (updateDTO == null || updateDTO.getId() == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        User user = this.getById(updateDTO.getId());
        if (user == null) {
            return false;
        }

        if (updateDTO.getUsername() != null && !updateDTO.getUsername().isBlank()) {
            String username = updateDTO.getUsername().trim();
            QueryWrapper<User> existsQuery = new QueryWrapper<>();
            existsQuery.eq("username", username).ne("id", updateDTO.getId());
            if (this.getOne(existsQuery) != null) {
                throw new IllegalArgumentException("用户名已存在");
            }
            user.setUsername(username);
        }
        if (updateDTO.getPassword() != null && !updateDTO.getPassword().isBlank()) {
            user.setPassword(updateDTO.getPassword());
        }
        if (updateDTO.getNickname() != null) {
            user.setNickname(updateDTO.getNickname());
        }
        if (updateDTO.getEmail() != null) {
            user.setEmail(updateDTO.getEmail());
        }
        if (updateDTO.getPhone() != null) {
            user.setPhone(updateDTO.getPhone());
        }
        if (updateDTO.getRole() != null) {
            user.setRole(updateDTO.getRole().name());
        }
        if (updateDTO.getStatus() != null) {
            if (updateDTO.getStatus() != 0 && updateDTO.getStatus() != 1) {
                throw new IllegalArgumentException("status must be 0 or 1");
            }
            user.setStatus(updateDTO.getStatus());
        }
        return this.updateById(user);
    }
}
