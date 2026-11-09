package com.chbcraft.auth.controller;

import com.chcbcraft.dto.User;
import com.chcbcraft.dto.context.UserContext;
import com.chcbcraft.dto.dto.EmailLoginRequest;
import com.chcbcraft.dto.dto.LoginRequest;
import com.chcbcraft.dto.dto.RegisterRequest;
import com.chcbcraft.dto.dto.SendEmailCodeRequest;
import com.chcbcraft.dto.enums.AuthRedisKey;
import com.chcbcraft.dto.vo.Result;
import com.chbcraft.auth.service.UserService;
import com.chbcraft.auth.util.JwtUtil;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 用户控制器（auth 域）— 注册 / 登录 / 登出 / 验证码 / 改密码
 * <p>
 * 从 note-module 迁入；资料接口（info/update-info）仍留在 note-module。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void init() {
        // 初始化 Redis Key（调试用固定验证码，用户要求保留）
        stringRedisTemplate.opsForValue().set(AuthRedisKey.CAPTCHA.build("devCapKey"), "head");
    }

    /**
     * 用户注册
     * POST /user/register
     */
    @PostMapping("/register")
    public Result<User> register(@RequestBody RegisterRequest req) {
        try {
            User user = userService.register(req);
            user.setPassword(null); // 密码不返回前端
            return Result.success("注册成功", user);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 用户登录
     * POST /user/login
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        try {
            Map<String, Object> data = userService.login(req);
            return Result.success("登录成功", data);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 退出登录
     * POST /user/logout
     * <p>
     * 将当前 token 加入 Redis 的 logout 黑名单 Set 中，
     * 过期时间设为 token 的最大过期时间，使该 token 立即失效。
     * <p>
     * key 统一使用 AuthRedisKey.LOGOUT_TOKEN（note_backbond:logout:token），
     * 与网关 GlobalJwtFilter 的检查口径一致（修复此前两处 key 不一致导致登出不生效的问题）。
     */
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        // 从请求头提取 token
        String authHeader = request.getHeader("Authorization");
        String token = authHeader.substring(7);

        String logoutKey = AuthRedisKey.LOGOUT_TOKEN.build();
        // 将 token 加入登出黑名单 Set，并设置 TTL = token 最大过期时间
        stringRedisTemplate.opsForSet().add(logoutKey, token);
        stringRedisTemplate.expire(logoutKey, jwtUtil.getExpirationTime(), TimeUnit.MILLISECONDS);

        return Result.success("退出登录成功");
    }

    /**
     * 通过 Token 获取用户信息（不依赖拦截器，手动校验 token）
     * GET /user/token-info?token=xxx
     * <p>
     * 适用于客户端持有 token 字符串但想查看所属用户信息的场景
     */
    @GetMapping("/token-info")
    public Result<User> getInfoByToken(@RequestParam("token") String token) {
        // 校验 token 是否为空
        if (token == null || token.trim().isEmpty()) {
            return Result.error("token 不能为空");
        }
        // 校验 token 签名
        if (!jwtUtil.validateToken(token)) {
            return Result.unauthorized("无效的认证令牌");
        }
        // 检查 token 是否已被登出（与网关/登出接口同一 key）
        Boolean isLoggedOut = stringRedisTemplate.opsForSet()
                .isMember(AuthRedisKey.LOGOUT_TOKEN.build(), token);
        if (Boolean.TRUE.equals(isLoggedOut)) {
            return Result.unauthorized("令牌已失效，请重新登录");
        }

        // 检查 token 是否过期
        if (jwtUtil.isTokenExpired(token)) {
            return Result.unauthorized("令牌已过期，请重新登录");
        }



        // 解析用户 ID 并查询用户信息
        int userId = jwtUtil.getUserIdFromToken(token);
        User user = userService.getUserInfo(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        user.setPassword(null);
        return Result.success(user);
    }

    /**
     * 修改密码
     * PUT /user/password
     * <p>
     * 当前用户 ID 由 UserContextInterceptor 从网关注入的 X-User-Id 头写入
     */
    @PutMapping("/password")
    public Result<String> changePassword(@RequestBody Map<String, String> body) {
        Integer userId = UserContext.getUserId();
        if (userId == null) {
            return Result.unauthorized("未登录");
        }
        try {
            userService.changePassword(userId, body.get("oldPwd"), body.get("newPwd"));
            return Result.success("密码修改成功");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 发送邮箱验证码
     * POST /user/send-email-code
     */
    @PostMapping("/send-email-code")
    public Result<String> sendEmailCode(@RequestBody SendEmailCodeRequest req) {
        try {
            userService.sendEmailCode(req.getEmail());
            return Result.success("验证码已发送到您的邮箱");
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 邮箱验证码登录（未注册邮箱自动创建账号）
     * POST /user/email-login
     */
    @PostMapping("/email-login")
    public Result<Map<String, Object>> emailLogin(@RequestBody EmailLoginRequest req) {
        try {
            Map<String, Object> data = userService.emailLogin(req.getEmail(), req.getCode());
            return Result.success("登录成功", data);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
    }
}
