package com.chcbcraft.dto.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AuthRedisKey 单元测试 — key 模式必须与拆分前 note-module 保持一致（存量数据兼容）
 */
class AuthRedisKeyTest {

    @Test
    void captchaKeyMatchesLegacyPattern() {
        assertEquals("note_backbond:captcha:uuid-1", AuthRedisKey.CAPTCHA.build("uuid-1"));
    }

    @Test
    void emailCodeKeyMatchesLegacyPattern() {
        assertEquals("note_backbond:email:code:a@b.com", AuthRedisKey.EMAIL_CODE.build("a@b.com"));
    }

    @Test
    void loginFailAndLockKeysMatchLegacyPattern() {
        assertEquals("note_backbond:login:fail:alice", AuthRedisKey.LOGIN_FAIL.build("alice"));
        assertEquals("note_backbond:login:lock:alice", AuthRedisKey.LOGIN_LOCK.build("alice"));
    }

    @Test
    void logoutTokenKeyIsUnifiedConstant() {
        // 统一后的登出黑名单 key（修复此前写入/检查不一致的 bug）
        assertEquals("note_backbond:logout:token", AuthRedisKey.LOGOUT_TOKEN.build());
    }
}
