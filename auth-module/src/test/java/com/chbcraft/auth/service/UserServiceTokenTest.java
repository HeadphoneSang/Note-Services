package com.chbcraft.auth.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.chbcraft.auth.mapper.UserMapper;
import com.chbcraft.auth.util.JwtUtil;
import com.chcbcraft.dto.User;
import com.chcbcraft.dto.dto.LoginRequest;
import com.chcbcraft.dto.enums.AuthRedisKey;
import com.chcbcraft.dto.enums.TokenType;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 单元测试 — 重点验证 login 的正常流程与各异常分支
 * <p>
 * Mapper / Redis / Email 全部 mock；JwtUtil 与 BCryptPasswordEncoder 用真实实例，
 * 保证「登录 → 签发双 token」整条链路真实可验证。
 */
@ExtendWith(MockitoExtension.class)
public class UserServiceTokenTest {

    /** 与 testGenerateToken 一致的测试密钥（长度满足 HS256 要求） */
    private static final String TEST_SECRET = "NoteBackbondSecretKey2026!@#$%^&*()VeryLongEnoughHS256";
    private static final long TEST_EXPIRATION = 604800000L;

    private static final String CAPTCHA_KEY = "captcha-uuid-001";
    private static final String CAPTCHA_CODE = "ab3d";
    private static final String ACCOUNT = "chb";
    private static final String PASSWORD = "password123";
    private static final int USER_ID = 1;

    @Mock
    private UserMapper userMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    /** login 不会用到，仅用于满足字段注入 */
    @Mock
    private EmailService emailService;

    @Spy
    private JwtUtil jwtUtil = new JwtUtil(TEST_SECRET, TEST_EXPIRATION);

    @Spy
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks
    private UserService userService;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        // 纯单测环境没有 MyBatis 启动流程，lambda 条件构造器需要手动注册实体的 TableInfo 缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
    }

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    public void testGenerateToken() {
        // TODO: test generateToken
        String sessionId = "sessionTest";
        JwtUtil util = new JwtUtil(TEST_SECRET, TEST_EXPIRATION);
        String accessToken = util.generateToken(1, "test", sessionId, TokenType.ACCESS_TOKEN);
        System.out.println(accessToken);
        System.out.println(util.validateToken(accessToken));
        System.out.println(util.getUserIdFromToken(accessToken));
        System.out.println(util.getAccountFromToken(accessToken));
        System.out.println(util.getSessionId(accessToken));
    }

    // ==================== login 测试 ====================

    /**
     * 正常登录：验证码有效、账号未锁定、密码正确 → 签发 accessToken / refreshToken
     */
    @Test
    public void testLogin() {
        mockValidCaptchaAndNoLock();
        User dbUser = buildDbUser();
        when(userMapper.selectOne(any())).thenReturn(dbUser);

        Map<String, Object> data = userService.login(buildLoginRequest(CAPTCHA_CODE));

        // 返回数据完整
        assertNotNull(data);
        String sessionId = (String) data.get("sessionId");
        String accessToken = (String) data.get("accessToken");
        String refreshToken = (String) data.get("refreshToken");
        User userInfo = (User) data.get("userInfo");
        assertNotNull(sessionId);
        assertNotNull(accessToken);
        assertNotNull(refreshToken);
        assertNotNull(userInfo);

        // accessToken 是真实有效的 JWT，claims 与登录用户一致
        assertTrue(jwtUtil.validateToken(accessToken));
        assertEquals(USER_ID, jwtUtil.getUserIdFromToken(accessToken));
        assertEquals(ACCOUNT, jwtUtil.getAccountFromToken(accessToken));
        assertEquals(sessionId, jwtUtil.getSessionId(accessToken));

        // 双 token 机制：refreshToken 与 accessToken 不同
        assertNotEquals(accessToken, refreshToken);

        // 返回的用户信息不携带密码
        assertNull(userInfo.getPassword());
        assertEquals(ACCOUNT, userInfo.getAccount());

        // 登录成功后清理失败计数；验证码一次性消费
        verify(stringRedisTemplate).delete(AuthRedisKey.LOGIN_FAIL.build(ACCOUNT));
        verify(stringRedisTemplate).delete(AuthRedisKey.CAPTCHA.build(CAPTCHA_KEY));
    }

    /**
     * 验证码错误 → 拒绝登录，且验证码已被消费（一次性）
     */
    @Test
    public void testLoginWithWrongCaptcha() {
        when(valueOperations.get(AuthRedisKey.CAPTCHA.build(CAPTCHA_KEY))).thenReturn(CAPTCHA_CODE);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.login(buildLoginRequest("0000")));

        assertEquals("验证码错误", ex.getMessage());
        verify(stringRedisTemplate).delete(AuthRedisKey.CAPTCHA.build(CAPTCHA_KEY));
    }

    /**
     * 验证码已过期（Redis 中不存在）→ 拒绝登录
     */
    @Test
    public void testLoginWithExpiredCaptcha() {
        when(valueOperations.get(AuthRedisKey.CAPTCHA.build(CAPTCHA_KEY))).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.login(buildLoginRequest(CAPTCHA_CODE)));

        assertEquals("验证码已过期，请刷新", ex.getMessage());
        verify(stringRedisTemplate).delete(AuthRedisKey.CAPTCHA.build(CAPTCHA_KEY));
    }

    /**
     * 账号被临时锁定（lock key 存在且 TTL > 0）→ 拒绝登录，且不应查库
     */
    @Test
    public void testLoginWithLockedAccount() {
        when(valueOperations.get(AuthRedisKey.CAPTCHA.build(CAPTCHA_KEY))).thenReturn(CAPTCHA_CODE);
        when(stringRedisTemplate.getExpire(AuthRedisKey.LOGIN_LOCK.build(ACCOUNT), TimeUnit.SECONDS))
                .thenReturn(600L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.login(buildLoginRequest(CAPTCHA_CODE)));

        assertTrue(ex.getMessage().contains("账号已被临时锁定"));
        verify(userMapper, never()).selectOne(any());
    }

    /**
     * 账号被永久锁定（lock key TTL = -1）→ 拒绝登录
     */
    @Test
    public void testLoginWithPermanentLockedAccount() {
        when(valueOperations.get(AuthRedisKey.CAPTCHA.build(CAPTCHA_KEY))).thenReturn(CAPTCHA_CODE);
        when(stringRedisTemplate.getExpire(AuthRedisKey.LOGIN_LOCK.build(ACCOUNT), TimeUnit.SECONDS))
                .thenReturn(-1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.login(buildLoginRequest(CAPTCHA_CODE)));

        assertEquals("账号已永久锁定", ex.getMessage());
    }

    /**
     * 密码错误 → 报「账号或密码错误」，并记录一次失败计数（首次失败设置 TTL）
     */
    @Test
    public void testLoginWithWrongPassword() {
        mockValidCaptchaAndNoLock();
        User user = buildDbUser();
        when(userMapper.selectOne(any())).thenReturn(user);
        when(valueOperations.increment(AuthRedisKey.LOGIN_FAIL.build(ACCOUNT))).thenReturn(1L);

        LoginRequest req = buildLoginRequest(CAPTCHA_CODE);
        req.setPassword("wrongPassword");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.login(req));

        assertEquals("账号或密码错误", ex.getMessage());
        verify(stringRedisTemplate).expire(
                AuthRedisKey.LOGIN_FAIL.build(ACCOUNT), 30L, TimeUnit.MINUTES);
    }

    /**
     * 账号不存在 → 同样报「账号或密码错误」（不泄露账号是否存在），并记录失败计数
     */
    @Test
    public void testLoginWithNonexistentAccount() {
        mockValidCaptchaAndNoLock();
        when(userMapper.selectOne(any())).thenReturn(null);
        when(valueOperations.increment(AuthRedisKey.LOGIN_FAIL.build(ACCOUNT))).thenReturn(1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> userService.login(buildLoginRequest(CAPTCHA_CODE)));

        assertEquals("账号或密码错误", ex.getMessage());
    }

    // ==================== 测试辅助 ====================

    private LoginRequest buildLoginRequest(String captchaCode) {
        LoginRequest req = new LoginRequest();
        req.setAccount(ACCOUNT);
        req.setPassword(PASSWORD);
        req.setCaptchaKey(CAPTCHA_KEY);
        req.setCaptchaCode(captchaCode);
        return req;
    }

    /** mock 「验证码存在于 Redis + 账号未被锁定」的公共前置状态 */
    private void mockValidCaptchaAndNoLock() {
        when(valueOperations.get(AuthRedisKey.CAPTCHA.build(CAPTCHA_KEY))).thenReturn(CAPTCHA_CODE);
        when(stringRedisTemplate.getExpire(AuthRedisKey.LOGIN_LOCK.build(ACCOUNT), TimeUnit.SECONDS))
                .thenReturn(-2L);
    }

    /** 构造库里的用户（密码为 BCrypt 加密后的真实值，供 matches 校验） */
    private User buildDbUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setAccount(ACCOUNT);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setNickname("测试用户");
        return user;
    }
}
