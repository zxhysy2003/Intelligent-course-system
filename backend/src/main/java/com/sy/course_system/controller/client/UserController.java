package com.sy.course_system.controller.client;

import com.sy.course_system.common.Result;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.dto.LoginDTO;
import com.sy.course_system.dto.UserRegisterDTO;
import com.sy.course_system.service.UserService;
import com.sy.course_system.vo.UserVO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户控制器
 * 处理用户相关的业务逻辑，包括用户注册、登录、查询个人信息等操作
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 获取所有用户列表
     * @return 返回用户列表，包含用户基本信息
     */
    @GetMapping("/list")
    public Result<List<UserVO>> list() {
        return Result.success(userService.listUsers());
    }

    /**
     * 获取指定用户的详细信息
     * @param id 用户ID
     * @return 返回用户详细信息，如果用户不存在返回错误
     */
    @GetMapping("/{id}")
    public Result<UserVO> detail(@PathVariable Long id) {
        UserVO userVO = userService.getUserById(id);
        if (userVO == null) {
            return Result.error(404, "用户不存在");
        }
        return Result.success(userVO);
    }

    /**
     * 用户注册
     * @param registerDTO 注册信息，包含用户名和密码
     * @return 返回注册结果，成功返回提示信息，失败返回对应错误信息
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody UserRegisterDTO registerDTO) {
        Integer res = userService.register(registerDTO);
        if (res == null) {
            return Result.error(500, "注册失败");
        } else if (res == -1) {
            return Result.error(400, "用户名已存在");
        }
        return Result.success("注册成功");
    }

    /**
     * 用户登录
     * @param loginDTO 登录信息，包含用户名和密码
     * @return 返回登录结果，成功返回JWT token，失败返回错误信息
     */
    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginDTO loginDTO) {
        String res = userService.login(loginDTO);
        if (res == null) {
            return Result.error(401, "用户名或密码错误");
        }
        return Result.success(res);
    }

    /**
     * 获取当前登录用户的个人信息
     * @return 返回当前用户的ID、用户名和角色信息
     */
    @GetMapping("/profile")
    public Result<Map<String, Object>> profile() {
        
        Map<String, Object> map = new HashMap<>();
        
        map.put("userId", UserContext.getUserId());
        map.put("username", UserContext.getUsername());
        map.put("role", UserContext.getRole());
        
        return Result.success(map);
    }

}
