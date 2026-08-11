package com.sy.course_system.controller.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import com.sy.course_system.common.ApiPaths;
import com.sy.course_system.common.PageResult;
import com.sy.course_system.common.Result;
import com.sy.course_system.dto.UserDeleteDTO;
import com.sy.course_system.dto.UserQueryDTO;
import com.sy.course_system.dto.UserUpdateDTO;
import com.sy.course_system.dto.UserRoleUpdateDTO;
import com.sy.course_system.dto.UserStatusUpdateDTO;
import com.sy.course_system.service.UserService;
import com.sy.course_system.vo.UserDetailVO;
import com.sy.course_system.vo.UserVO;

@RestController
@RequestMapping(ApiPaths.ADMIN_USERS)
public class UserAdminController {
    @Autowired
    private UserService userService;

    @PostMapping("/search")
    public Result<PageResult<UserVO>> listUsers(@RequestBody UserQueryDTO query) {
        return Result.success(userService.pageForAdmin(query));
    }

    @GetMapping("/{userId}")
    public Result<UserDetailVO> detail(@PathVariable Long userId) {
        UserDetailVO vo = userService.getUserDetailForAdmin(userId);
        if (vo == null) {
            return Result.error(404, "用户不存在");
        }
        return Result.success(vo);
    }

    @PatchMapping("/{userId}/role")
    public Result<String> updateRole(@PathVariable Long userId, @RequestBody UserRoleUpdateDTO request) {
        try {
            boolean ok = userService.updateUserRole(userId, request == null ? null : request.getRole());
            if (!ok) {
                return Result.error(404, "用户不存在");
            }
            return Result.success("更新成功");
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        }
    }

    @PatchMapping("/{userId}/status")
    public Result<String> updateStatus(@PathVariable Long userId, @RequestBody UserStatusUpdateDTO request) {
        try {
            boolean ok = userService.updateUserStatus(userId, request == null ? null : request.getStatus());
            if (!ok) {
                return Result.error(404, "用户不存在");
            }
            return Result.success("更新成功");
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        }
    }

    @PutMapping("/{userId}")
    public Result<String> update(@PathVariable Long userId, @RequestBody UserUpdateDTO updateDTO) {
        try {
            updateDTO.setId(userId);
            boolean ok = userService.updateUser(updateDTO);
            if (!ok) {
                return Result.error(404, "用户不存在");
            }
            return Result.success("更新成功");
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        }
    }

    @DeleteMapping
    public Result<String> deleteUsers(@RequestBody UserDeleteDTO dto) {
        Integer deleted = userService.removeUsers(dto == null ? null : dto.getUserIds());
        if (deleted == null || deleted == 0) {
            return Result.error(400, "userIds 不能为空");
        }
        if (deleted == -1) {
            return Result.error(404, "用户不存在");
        }
        return Result.success("删除成功");
    }
}
