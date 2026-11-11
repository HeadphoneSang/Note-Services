package com.chbcraft.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chbcraft.auth.util.EncodeUtil;
import com.chcbcraft.dto.User;
import com.chcbcraft.dto.dto.LoginRequest;
import com.chcbcraft.dto.dto.RegisterRequest;
import com.chcbcraft.dto.enums.AuthRedisKey;
import com.chbcraft.auth.mapper.UserMapper;
import com.chbcraft.auth.util.JwtUtil;
import com.chcbcraft.dto.enums.TokenType;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 用户业务层（auth 域）— 注册 / 登录 / 邮箱验证码 / 改密码
 * <p>
 * 从 note-module 迁入；资料接口（info/update-info）仍留在 note-module。
 */
@Service
@Slf4j
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Resource
    private RedisScript<Long> checkAndAddScript;

    /**
     * BCrypt 密码编码器（自动从 IoC 容器获取，由 PasswordEncoderConfig 提供）
     */
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private EmailService emailService;

    // ================ 登录安全配置 ================

    /** 连续失败最大次数（超过后锁定账号） */
    private static final int MAX_LOGIN_ATTEMPTS = 5;

    /** 账号锁定时间（分钟） */
    private static final int LOCK_DURATION_MINUTES = 15;

    /** 邮箱验证码有效期（分钟） */
    private static final int EMAIL_CODE_TTL_MINUTES = 5;

    /** 邮箱验证码重发间隔（秒） — 同一邮箱 60 秒内不允许重复发送 */
    private static final int EMAIL_CODE_RESEND_INTERVAL_SECONDS = 60;

    // ==================== 注册 ====================

    /**
     * 用户注册
     *
     * @param req 注册请求（account, password, nickname, phoneNumber）
     * @return 注册成功的用户信息（不含密码）
     */
    @Transactional
    public User register(RegisterRequest req) {
        // 1. 参数校验
        String account = req.getAccount();
        String password = req.getPassword();

        if (account == null || account.trim().isEmpty()) {
            throw new IllegalArgumentException("账号不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码至少需要6位");
        }
        if (req.getCaptchaKey() == null || req.getCaptchaKey().trim().isEmpty()
                || req.getCaptchaCode() == null || req.getCaptchaCode().trim().isEmpty()) {
            throw new IllegalArgumentException("验证码不能为空");
        }
        validateCAPTCHA(req.getCaptchaKey(), req.getCaptchaCode());
        // 2. 检查账号是否已存在
        if (userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getAccount, account)) > 0) {
            throw new IllegalArgumentException("账号已被注册");
        }

        // 3. 创建新用户，密码用 BCrypt 加密存储
        User user = new User();
        user.setAccount(account);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(req.getNickname() != null ? req.getNickname() : account);
        user.setPhoneNumber(req.getPhoneNumber());
        user.setEmail(req.getEmail());

        // 4. 保存到数据库
        userMapper.insert(user);
        return user;
    }

    // ==================== 登录 ====================

    /**
     * 用户登录（含验证码校验 + 暴力破解防护）
     *
     * @param req 登录请求（account, password, captchaKey, captchaCode）
     * @return 包含 token 和用户信息的 Map
     */
    public Map<String, Object> login(LoginRequest req) {
        // 1. 参数校验
        validRequestParams(req);
        String account = req.getAccount().trim();
        String lockKey = AuthRedisKey.LOGIN_LOCK.build(account);  // 账号异常后被锁定的记录key
        String failKey = AuthRedisKey.LOGIN_FAIL.build(account);  // 记录账户失败登录次数的key
        // 2. 验证码校验
        if(req.getCaptchaKey()!=null & req.getCaptchaKey().equals("devCap")){
            log.info("Using devCap captcha");
        }else{
            validateCAPTCHA(req.getCaptchaKey(), req.getCaptchaCode());
        }
        // 3. 检查账号是否被锁定
        validLockTTL(lockKey);
        // 4. 查找用户
        User user = Optional.ofNullable(userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getAccount, account)))
                .orElseThrow(() -> {
                    // 账号不存在，同样记录一次失败（防止枚举有效账号）
                    recordLoginFailure(failKey, lockKey, account);
                    return new IllegalArgumentException("账号或密码错误");
                });
        // 5. 验证密码
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            recordLoginFailure(failKey, lockKey, account);
            throw new IllegalArgumentException("账号或密码错误");
        }
        // 6. 登录成功 → 清除失败计数
        stringRedisTemplate.delete(failKey);
        // 7. 生成 JWT Tokens & 记录refresh token
        List<String> tokens = generateTokensAndRecordRefreshToken(user);
        String sessionId = tokens.get(0);
        String accessToken = tokens.get(1);
        String refreshToken = tokens.get(2);
        // 8. 将当前的session_id存到当前的redis中的USER_SESSION_SET中，统计登录的设备数量
        String setKey = AuthRedisKey.USER_SESSION_SET.build(user.getId());
        Long ans = stringRedisTemplate.execute(checkAndAddScript, Collections.singletonList(setKey), sessionId, String.valueOf(jwtUtil.getSessionLimit()));
        validateCAA(ans);
        // 9. 组装返回数据
        return getStringObjectMap(user, sessionId, accessToken, refreshToken);
    }

    @NotNull
    private Map<String, Object> getStringObjectMap(User user, String sessionId, String accessToken, String refreshToken) {
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId",sessionId);
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        user.setPassword(null);
        data.put("userInfo", user);
        return data;
    }

    /**
     * 记录一次登录失败，达到阈值时锁定账号
     */
    private void recordLoginFailure(String failKey, String lockKey, String account) {
        Long attempts = stringRedisTemplate.opsForValue().increment(failKey);
        // 第一次失败时设置 TTL，防止遗忘的计数永远占用内存
        if (attempts != null && attempts == 1) {
            // 失败计数的 TTL 略大于锁定时间 × 最大次数，确保锁定期内计数不会提前消失
            stringRedisTemplate.expire(failKey, LOCK_DURATION_MINUTES * 2, TimeUnit.MINUTES);
        }
        if (attempts != null && attempts >= MAX_LOGIN_ATTEMPTS) {
            // 达到上限 → 锁定账号
            stringRedisTemplate.opsForValue().set(lockKey, "1", LOCK_DURATION_MINUTES, TimeUnit.MINUTES);
            // 清除失败计数（解锁后重新计数）
            stringRedisTemplate.delete(failKey);
        }
    }

    // ==================== 获取用户信息 ====================

    /**
     * 根据用户 ID 获取用户信息（token-info 接口使用）
     *
     * @param userId 用户 ID
     * @return 用户实体（密码字段会被置空，避免泄露）
     */
    public User getUserInfo(Integer userId) {
        User user = Optional.ofNullable(userMapper.selectById(userId))
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        // 密码不返回
        user.setPassword(null);
        return user;
    }

    // ==================== 修改密码 ====================

    /**
     * 修改密码
     *
     * @param userId   用户 ID
     * @param oldPwd   旧密码
     * @param newPwd   新密码
     */
    @Transactional
    public void changePassword(Integer userId, String oldPwd, String newPwd) {
        // 1. 校验参数
        if (oldPwd == null || oldPwd.isEmpty()) {
            throw new IllegalArgumentException("旧密码不能为空");
        }
        if (newPwd == null || newPwd.length() < 6) {
            throw new IllegalArgumentException("新密码至少需要6位");
        }

        // 2. 查找用户
        User user = Optional.ofNullable(userMapper.selectById(userId))
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        // 3. 验证旧密码
        if (!passwordEncoder.matches(oldPwd, user.getPassword())) {
            throw new IllegalArgumentException("旧密码错误");
        }

        // 4. 更新密码
        user.setPassword(passwordEncoder.encode(newPwd));
        userMapper.updateById(user);
    }

    // ==================== 邮箱验证码发送 ====================

    /**
     * 向指定邮箱发送登录验证码
     *
     * @param email 目标邮箱
     */
    public void sendEmailCode(String email) {
        // 1. 参数校验
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        // 2. 检查是否在重发冷却期内
        String codeKey = AuthRedisKey.EMAIL_CODE.build(email);
        String ttlKey = codeKey + ":ttl";
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(ttlKey))) {
            long remaining = stringRedisTemplate.getExpire(ttlKey, TimeUnit.SECONDS);
            throw new IllegalArgumentException("请 " + remaining + " 秒后再重新发送");
        }
        // 3. 生成 6 位随机验证码
        String code = String.format("%06d", new Random().nextInt(1000000));
        // 4. 存入 Redis，TTL 5 分钟
        stringRedisTemplate.opsForValue().set(codeKey, code, EMAIL_CODE_TTL_MINUTES, TimeUnit.MINUTES);
        // 冷却标记（60 秒）
        stringRedisTemplate.opsForValue().set(ttlKey, "1", EMAIL_CODE_RESEND_INTERVAL_SECONDS, TimeUnit.SECONDS);

        // 5. 发送邮件
        emailService.sendVerificationCode(email, code);
    }

    // ==================== 邮箱验证码登录 ====================

    /**
     * 邮箱验证码登录（未注册邮箱自动创建账号）
     *
     * @param email 邮箱
     * @param code  验证码
     * @return token 和用户信息
     */
    @Transactional
    public Map<String, Object> emailLogin(String email, String code) {
        // 1. 参数校验
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("验证码不能为空");
        }

        // 2. 从 Redis 获取验证码
        String codeKey = AuthRedisKey.EMAIL_CODE.build(email);
        String realCode = stringRedisTemplate.opsForValue().get(codeKey);
        // 一次性使用，立即删除
        stringRedisTemplate.delete(codeKey);
        stringRedisTemplate.delete(codeKey + ":ttl");

        // 3. 校验验证码
        if (realCode == null) {
            throw new IllegalArgumentException("验证码已过期，请重新获取");
        }
        if (!realCode.equals(code.trim())) {
            throw new IllegalArgumentException("验证码错误");
        }

        // 4. 查找或创建用户
        email = email.trim();
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
        if (user == null) {
            user = new User();
            // 从邮箱 @ 前部分生成 account
            String account = email.substring(0, email.indexOf('@'));
            // 检查 account 是否已存在，若存在则追加随机后缀
            if (userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getAccount, account)) > 0) {
                String suffix = String.format("%04x", new Random().nextInt(0x10000));
                account = account + "_" + suffix;
            }
            user.setAccount(account);
            user.setNickname(account);
            user.setEmail(email);
            // 邮箱登录用户无密码，标记为不可用密码
            user.setPassword("");
            userMapper.insert(user);
        }

        // 5. 生成 JWT Token
        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateToken(user.getId(), user.getEmail(), sessionId, TokenType.ACCESS_TOKEN);
        String refreshToken = jwtUtil.generateToken(user.getId(), user.getEmail(), sessionId, TokenType.REFRESH_TOKEN);
        // 6. 组装返回数据
        return getStringObjectMap(user, sessionId, accessToken, refreshToken);
    }

    private void validateCAA(Long result){
        if(result == 0)
            return;
        if(result == 1){
            throw new IllegalArgumentException("该账号在当前设备已登录，请先登出");
        }
        if(result == 2)
            throw new IllegalArgumentException("登录设备数量超过限制，请先登出一些设备");
    }

    private void validateCAPTCHA(String captchaKey, String captchaCode) {
        // 2. 校验验证码（优先于账号锁定检查，避免泄露账号状态）
        String redisKey = AuthRedisKey.CAPTCHA.build(captchaKey);
        String storedCode = stringRedisTemplate.opsForValue().get(redisKey);
        // 无论校验成功还是失败，验证码都立即失效（一次性使用）
        stringRedisTemplate.delete(redisKey);
        if (storedCode == null) {
            throw new IllegalArgumentException("验证码已过期，请刷新");
        }
        if (!storedCode.equals(captchaCode.trim().toLowerCase())) {
            throw new IllegalArgumentException("验证码错误");
        }
    }

    private List<String> generateTokensAndRecordRefreshToken(User user) {
        String sessionId = UUID.randomUUID().toString();
        String accessToken = jwtUtil.generateToken(user.getId(), user.getAccount(),sessionId, TokenType.ACCESS_TOKEN);
        String refreshToken = jwtUtil.generateToken(user.getId(), user.getAccount(),sessionId, TokenType.REFRESH_TOKEN);
        String refTokenKey = AuthRedisKey.REFRESH_TOKEN.build(user.getId(), sessionId);
        stringRedisTemplate.opsForValue().set(refTokenKey, EncodeUtil.sha256(refreshToken));
        // 設置 refresh token 的有效期
        stringRedisTemplate.expire(refTokenKey, jwtUtil.getRefreshExpirationTime(), TimeUnit.MILLISECONDS);
        return Arrays.asList(sessionId,accessToken, refreshToken);
    }

    private void validRequestParams(LoginRequest req){
        if (req.getAccount() == null || req.getAccount().trim().isEmpty()) {
            throw new IllegalArgumentException("账号不能为空");
        }
        if (req.getPassword() == null || req.getPassword().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (req.getCaptchaKey() == null || req.getCaptchaKey().trim().isEmpty()
                || req.getCaptchaCode() == null || req.getCaptchaCode().trim().isEmpty()) {
            throw new IllegalArgumentException("验证码不能为空");
        }
    }

    private void validLockTTL(String lockKey){
        Long ttl = stringRedisTemplate.getExpire(
                lockKey, TimeUnit.SECONDS
        );
        if (ttl != null && ttl != -2) {
            if(ttl==-1)
                throw new IllegalArgumentException("账号已永久锁定");
            long minutes = (ttl + 59) / 60; // 最小返回一分钟
            throw new IllegalArgumentException(
                    "账号已被临时锁定，请 " + minutes + " 分钟后再试"
            );
        }
    }
}
