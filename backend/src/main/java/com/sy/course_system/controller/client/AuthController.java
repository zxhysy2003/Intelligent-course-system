package com.sy.course_system.controller.client;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sy.course_system.common.ApiPaths;
import com.sy.course_system.common.Result;
import com.sy.course_system.dto.LoginDTO;
import com.sy.course_system.dto.UserRegisterDTO;
import com.sy.course_system.service.UserService;

@RestController
@RequestMapping(ApiPaths.AUTH)
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Result<String> register(@RequestBody UserRegisterDTO registerDTO) {
        Integer result = userService.register(registerDTO);
        if (result == null) {
            return Result.error(500, "注册失败");
        }
        if (result == -1) {
            return Result.error(400, "用户名已存在");
        }
        return Result.success("注册成功");
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginDTO loginDTO) {
        String token = userService.login(loginDTO);
        if (token == null) {
            return Result.error(401, "用户名或密码错误");
        }
        return Result.success(token);
    }
}
