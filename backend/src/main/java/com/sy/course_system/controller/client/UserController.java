package com.sy.course_system.controller.client;

import com.sy.course_system.common.ApiPaths;
import com.sy.course_system.common.Result;
import com.sy.course_system.common.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器
 * 当前登录用户信息接口。
 */
@RestController
@RequestMapping(ApiPaths.USERS)
public class UserController {
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", UserContext.getUserId());
        map.put("username", UserContext.getUsername());
        map.put("role", UserContext.getRole());
        return Result.success(map);
    }
}
