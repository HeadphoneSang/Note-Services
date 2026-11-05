package com.chbcraft.deploy.security;

import com.chbcraft.deploy.config.DeployProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BearerTokenValidator 单元测试 — 覆盖鉴权头缺失/格式错误/token 错误/未配置 token 等路径
 */
class BearerTokenValidatorTest {

    private static final String TOKEN = "gh-secret-token-123";

    private BearerTokenValidator validator(String configuredToken) {
        return new BearerTokenValidator(new DeployProperties(configuredToken, "scripts/deploy.sh"));
    }

    @Test
    void 正确的BearerToken应通过校验() {
        assertTrue(validator(TOKEN).validate("Bearer " + TOKEN));
    }

    @Test
    void 缺失Authorization头应拒绝() {
        assertFalse(validator(TOKEN).validate(null));
    }

    @Test
    void 非Bearer方案应拒绝() {
        assertFalse(validator(TOKEN).validate("Basic " + TOKEN));
    }

    @Test
    void 只有前缀无token应拒绝() {
        assertFalse(validator(TOKEN).validate("Bearer "));
    }

    @Test
    void 错误token应拒绝() {
        assertFalse(validator(TOKEN).validate("Bearer wrong-token"));
    }

    @Test
    void 前缀匹配但更长的token应拒绝() {
        assertFalse(validator(TOKEN).validate("Bearer " + TOKEN + "extra"));
    }

    @Test
    void 前缀匹配但更短的token应拒绝() {
        assertFalse(validator(TOKEN).validate("Bearer " + TOKEN.substring(0, TOKEN.length() - 1)));
    }

    @Test
    void 未配置token时应拒绝一切请求() {
        assertFalse(validator("").validate("Bearer " + TOKEN));
        assertFalse(validator(null).validate("Bearer " + TOKEN));
    }
}
