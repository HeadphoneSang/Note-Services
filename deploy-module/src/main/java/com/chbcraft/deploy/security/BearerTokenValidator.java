package com.chbcraft.deploy.security;

import com.chbcraft.deploy.config.DeployProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Bearer Token 校验器 — 校验 Authorization 头是否携带正确的 webhook token
 * <p>
 * 用 MessageDigest.isEqual 做常数时间比较，避免逐字节短路比较带来的时序侧信道。
 */
@Component
public class BearerTokenValidator {

    private static final String BEARER_PREFIX = "Bearer ";

    /** 配置的期望 token；为空表示未配置，拒绝所有请求 */
    private final byte[] expectedToken;

    public BearerTokenValidator(DeployProperties properties) {
        this.expectedToken = properties.getWebhookToken() == null
                ? new byte[0]
                : properties.getWebhookToken().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 校验 Authorization 头
     *
     * @param authorizationHeader 原始 Authorization 头（可能为 null）
     * @return true = token 正确
     */
    public boolean validate(String authorizationHeader) {
        if (authorizationHeader == null || expectedToken.length == 0) {
            return false;
        }
        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            return false;
        }
        byte[] provided = authorizationHeader.substring(BEARER_PREFIX.length())
                .getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedToken, provided);
    }
}
