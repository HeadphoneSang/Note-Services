package com.chcbcraft.dto.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AuthRedisKey 单元测试 — 断言当前 auth: 命名空间下的 key 形态
 * <p>
 * key 前缀已由拆分前的 note_backbond: 迁移为 auth:（a3b2852 统一认证域 key），
 * 本测试跟随新契约；存量 Redis 数据若仍在旧前缀下，需自行迁移，不再由 key 模式兼容。
 */
class AuthRedisKeyTest {

    @Test
    void captchaKeyUsesAuthNamespace() {
        assertEquals("auth:captcha:uuid-1", AuthRedisKey.CAPTCHA.build("uuid-1"));
    }

    @Test
    void emailCodeKeyUsesAuthNamespace() {
        assertEquals("auth:email:code:a@b.com", AuthRedisKey.EMAIL_CODE.build("a@b.com"));
    }

    @Test
    void loginFailAndLockKeysUseAuthNamespace() {
        assertEquals("auth:login:fail:alice", AuthRedisKey.LOGIN_FAIL.build("alice"));
        assertEquals("auth:login:lock:alice", AuthRedisKey.LOGIN_LOCK.build("alice"));
    }

    @Test
    void logoutTokenKeyIsUnifiedConstant() {
        // 统一后的登出黑名单 key（修复此前写入/检查不一致的 bug）
        assertEquals("auth:logout:token", AuthRedisKey.LOGOUT_TOKEN.build());
    }
}
