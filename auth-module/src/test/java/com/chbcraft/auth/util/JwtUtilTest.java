package com.chbcraft.auth.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JwtUtil 单元测试 — 默认密钥与拆分前 note-module 硬编码一致，保证存量 token 兼容
 */
class JwtUtilTest {

    /** 拆分前 note-module JwtUtil 硬编码的密钥 */
    private static final String LEGACY_SECRET = "NoteBackbondSecretKey2026!@#$%^&*()VeryLongEnoughHS256";

    private final JwtUtil jwtUtil = new JwtUtil(LEGACY_SECRET, 7 * 24 * 60 * 60 * 1000L);

    @Test
    void generateThenValidateRoundTrip() {
        String token = jwtUtil.generateToken(42, "alice");

        assertTrue(jwtUtil.validateToken(token));
        assertFalse(jwtUtil.isTokenExpired(token));
        assertEquals(42, jwtUtil.getUserIdFromToken(token));
        assertEquals("alice", jwtUtil.getAccountFromToken(token));
    }

    @Test
    void tamperedTokenIsInvalid() {
        String token = jwtUtil.generateToken(42, "alice");
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertFalse(jwtUtil.validateToken(tampered));
        assertEquals(0, jwtUtil.getUserIdFromToken(tampered));
        assertNull(jwtUtil.getAccountFromToken(tampered));
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        // 网关侧密钥若与签发方不一致，token 必须被拒
        JwtUtil otherKeyUtil = new JwtUtil("AnotherSecretKey2026!@#$%^&*()AlsoLongEnoughHS256!!", 604800000L);
        String token = jwtUtil.generateToken(42, "alice");

        assertFalse(otherKeyUtil.validateToken(token));
    }

    @Test
    void garbageTokenIsInvalid() {
        assertFalse(jwtUtil.validateToken("not-a-jwt"));
        assertTrue(jwtUtil.isTokenExpired(""));
    }

    @Test
    void expirationConfigIsRespected() {
        JwtUtil oneHour = new JwtUtil(LEGACY_SECRET, 3600_000L);
        assertEquals(3600_000L, oneHour.getExpirationTime());
    }
}
