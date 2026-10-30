package org.template.controller;

import com.chcbcraft.dto.context.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.template.model.User;
import org.template.model.dto.UpdateUserRequest;
import org.template.model.vo.Result;
import org.template.service.UserService;

/**
 * 用户控制器（note 域）— 仅保留资料接口
 * <p>
 * 注册/登录/登出/验证码/改密码已迁往 auth-module（/api/auth/**）。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 获取当前登录用户信息
     * GET /user/info
     */
    @GetMapping("/info")
    public Result<User> getInfo() {
        Integer userId = UserContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("未登录");
        }
        User user = userService.getUserInfo(userId);
        return Result.success(user);
    }

    /**
     * 更新用户信息
     * PUT /user/update-info
     */
    @PutMapping("/update-info")
    public Result<User> updateInfo(@RequestBody UpdateUserRequest req) {
        Integer userId = UserContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("未登录");
        }
        try {
            User user = userService.updateUserInfo(userId, req);
            return Result.success("更新成功", user);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
}
