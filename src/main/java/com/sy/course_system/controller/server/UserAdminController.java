package com.sy.course_system.controller.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.sy.course_system.common.PageResult;
import com.sy.course_system.common.Result;
import com.sy.course_system.dto.UserDeleteDTO;
import com.sy.course_system.dto.UserQueryDTO;
import com.sy.course_system.dto.UserUpdateDTO;
import com.sy.course_system.enums.UserType;
import com.sy.course_system.service.UserService;
import com.sy.course_system.vo.UserDetailVO;
import com.sy.course_system.vo.UserVO;

@RestController
@RequestMapping("/admin/user")
public class UserAdminController {
    @Autowired
    private UserService userService;

    @PostMapping("/list")
    public Result<PageResult<UserVO>> listUsers(@RequestBody UserQueryDTO query) {
        return Result.success(userService.pageForAdmin(query));
    }

    @GetMapping("/detail/{userId}")
    public Result<UserDetailVO> detail(@PathVariable Long userId) {
        UserDetailVO vo = userService.getUserDetailForAdmin(userId);
        if (vo == null) {
            return Result.error(404, "用户不存在");
        }
        return Result.success(vo);
    }

    @PutMapping("/role/{userId}")
    public Result<String> updateRole(@PathVariable Long userId, @RequestParam UserType role) {
        try {
            boolean ok = userService.updateUserRole(userId, role);
            if (!ok) {
                return Result.error(404, "用户不存在");
            }
            return Result.success("更新成功");
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        }
    }

    @PutMapping("/status/{userId}")
    public Result<String> updateStatus(@PathVariable Long userId, @RequestParam Integer status) {
        try {
            boolean ok = userService.updateUserStatus(userId, status);
            if (!ok) {
                return Result.error(404, "用户不存在");
            }
            return Result.success("更新成功");
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        }
    }

    @PutMapping("/update")
    public Result<String> update(@RequestBody UserUpdateDTO updateDTO) {
        try {
            boolean ok = userService.updateUser(updateDTO);
            if (!ok) {
                return Result.error(404, "用户不存在");
            }
            return Result.success("更新成功");
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        }
    }

    @DeleteMapping("/delete")
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
