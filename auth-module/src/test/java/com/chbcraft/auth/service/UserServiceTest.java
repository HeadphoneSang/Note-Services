package com.chbcraft.auth.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.chcbcraft.dto.User;
import com.chcbcraft.dto.dto.LoginRequest;
import com.chcbcraft.dto.dto.RegisterRequest;
import com.chcbcraft.dto.enums.AuthRedisKey;
import com.chbcraft.auth.mapper.UserMapper;
import com.chbcraft.auth.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 单元测试 — 覆盖注册/登录/锁定/改密码/邮箱登录核心路径
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    /** 与拆分前 note-module 硬编码一致（存量 token 兼容） */
    private static final String SECRET = "NoteBackbondSecretKey2026!@#$%^&*()VeryLongEnoughHS256";

    @Mock
    private UserMapper userMapper;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private EmailService emailService;

    private UserService userService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        userService = new UserService();
        inject(userMapper);
        inject(new JwtUtil(SECRET, 604800000L));
        inject(encoder);
        inject(redis);
        inject(emailService);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    private void inject(Object dependency) {
        try {
            var field = UserService.class.getDeclaredFields();
            for (var f : field) {
                f.setAccessible(true);
                if (dependency != null && f.getType().isAssignableFrom(dependency.getClass())) {
                    f.set(userService, dependency);
                    return;
                }
            }
            throw new IllegalStateException("no field for " + dependency);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void stubCaptcha(String captchaKey, String storedCode) {
        when(valueOps.get(AuthRedisKey.CAPTCHA.build(captchaKey))).thenReturn(storedCode);
    }

    private User dbUser(int id, String account, String rawPassword) {
        User user = new User();
        user.setId(id);
        user.setAccount(account);
        user.setPassword(rawPassword == null ? "" : encoder.encode(rawPassword));
        user.setNickname(account);
        return user;
    }

    // ==================== 注册 ====================

    @Test
    void registerSuccessStoresBcryptPassword() {
        stubCaptcha("cap-1", "abcd");
        when(userMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            inv.getArgument(0, User.class).setId(1);
            return 1;
        }).when(userMapper).insert(any(User.class));

        RegisterRequest req = new RegisterRequest();
        req.setAccount("alice");
        req.setPassword("123456");
        req.setCaptchaKey("cap-1");
        req.setCaptchaCode("ABCD");

        User user = userService.register(req);

        assertEquals("alice", user.getAccount());
        assertNotEquals("123456", user.getPassword());
        assertTrue(encoder.matches("123456", user.getPassword()));
        // 验证码一次性使用：校验后立即删除
        verify(redis).delete(AuthRedisKey.CAPTCHA.build("cap-1"));
    }

    @Test
    void registerWithWrongCaptchaRejected() {
        stubCaptcha("cap-1", "abcd");

        RegisterRequest req = new RegisterRequest();
        req.setAccount("alice");
        req.setPassword("123456");
        req.setCaptchaKey("cap-1");
        req.setCaptchaCode("zzzz");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userService.register(req));
        assertEquals("验证码错误", e.getMessage());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void registerWithDuplicateAccountRejected() {
        stubCaptcha("cap-1", "abcd");
        when(userMapper.selectCount(any())).thenReturn(1L);

        RegisterRequest req = new RegisterRequest();
        req.setAccount("alice");
        req.setPassword("123456");
        req.setCaptchaKey("cap-1");
        req.setCaptchaCode("abcd");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userService.register(req));
        assertEquals("账号已被注册", e.getMessage());
    }

    // ==================== 登录 ====================

    @Test
    void loginSuccessReturnsTokenAndUserInfo() {
        stubCaptcha("cap-1", "abcd");
        when(redis.hasKey(AuthRedisKey.LOGIN_LOCK.build("alice"))).thenReturn(false);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(dbUser(7, "alice", "right-pw"));

        LoginRequest req = new LoginRequest();
        req.setAccount("alice");
        req.setPassword("right-pw");
        req.setCaptchaKey("cap-1");
        req.setCaptchaCode("abcd");

        Map<String, Object> data = userService.login(req);

        assertNotNull(data.get("token"));
        User userInfo = (User) data.get("userInfo");
        assertEquals(7, userInfo.getId());
        assertNull(userInfo.getPassword()); // 密码不返回
    }

    @Test
    void loginWithWrongPasswordRecordsFailure() {
        stubCaptcha("cap-1", "abcd");
        when(redis.hasKey(anyString())).thenReturn(false);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(dbUser(7, "alice", "right-pw"));
        when(valueOps.increment(AuthRedisKey.LOGIN_FAIL.build("alice"))).thenReturn(1L);

        LoginRequest req = new LoginRequest();
        req.setAccount("alice");
        req.setPassword("wrong-pw");
        req.setCaptchaKey("cap-1");
        req.setCaptchaCode("abcd");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userService.login(req));
        assertEquals("账号或密码错误", e.getMessage());
        // 第一次失败会设置失败计数 TTL
        verify(redis).expire(eq(AuthRedisKey.LOGIN_FAIL.build("alice")), anyLong(), any(TimeUnit.class));
    }

    @Test
    void loginOnLockedAccountRejected() {
        stubCaptcha("cap-1", "abcd");
        when(redis.hasKey(AuthRedisKey.LOGIN_LOCK.build("alice"))).thenReturn(true);
        when(redis.getExpire(anyString(), any(TimeUnit.class))).thenReturn(5L);

        LoginRequest req = new LoginRequest();
        req.setAccount("alice");
        req.setPassword("whatever");
        req.setCaptchaKey("cap-1");
        req.setCaptchaCode("abcd");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userService.login(req));
        assertTrue(e.getMessage().contains("账号已被临时锁定"));
    }

    // ==================== 改密码 ====================

    @Test
    void changePasswordWithCorrectOldPassword() {
        User user = dbUser(7, "alice", "old-pw");
        when(userMapper.selectById(7)).thenReturn(user);

        userService.changePassword(7, "old-pw", "new-pw123");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertTrue(encoder.matches("new-pw123", captor.getValue().getPassword()));
    }

    @Test
    void changePasswordWithWrongOldPasswordRejected() {
        when(userMapper.selectById(7)).thenReturn(dbUser(7, "alice", "old-pw"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userService.changePassword(7, "bad", "new-pw123"));
        assertEquals("旧密码错误", e.getMessage());
        verify(userMapper, never()).updateById(any(User.class));
    }

    // ==================== 邮箱验证码登录 ====================

    @Test
    void emailLoginCreatesUserForNewEmail() {
        when(valueOps.get(AuthRedisKey.EMAIL_CODE.build("a@b.com"))).thenReturn("123456");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(userMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> {
            inv.getArgument(0, User.class).setId(9);
            return 1;
        }).when(userMapper).insert(any(User.class));

        Map<String, Object> data = userService.emailLogin("a@b.com", "123456");

        assertNotNull(data.get("token"));
        User created = (User) data.get("userInfo");
        assertEquals("a", created.getAccount()); // 邮箱 @ 前缀作为账号
        assertNull(created.getPassword());
    }

    @Test
    void emailLoginWithWrongCodeRejected() {
        when(valueOps.get(AuthRedisKey.EMAIL_CODE.build("a@b.com"))).thenReturn("123456");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> userService.emailLogin("a@b.com", "999999"));
        assertEquals("验证码错误", e.getMessage());
    }
}
