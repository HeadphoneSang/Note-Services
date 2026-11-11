package com.chcbcraft.dto.enums;

/**
 * 认证域 Redis Key — auth 服务与 gateway 共用
 * <p>
 * key 模式与拆分前 note-module 的 RedisKey 保持一致，保证存量 Redis 数据兼容。
 * logout 黑名单此前存在「写入 key 与检查 key 不一致」的 bug，
 * 现统一以 {@link #LOGOUT_TOKEN} 为准。
 */
public enum AuthRedisKey {

    /** 用户会话 Set，参数 userId */
    USER_SESSION_SET("auth:login:session:%s"),

    /** 刷新 Token，参数 userId, sessionId */
    REFRESH_TOKEN("auth:token:ref:%s:%s"),

    /** 过期的刷新 Token，参数 userId, sessionId */
    EXPIRE_REFRESH_TOKEN("auth:token:exp:%s:%s"),

    /** 图形验证码，参数 uuid */
    CAPTCHA      ("auth:captcha:%s"),

    /** 邮箱验证码，参数 email */
    EMAIL_CODE   ("auth:email:code:%s"),

    /** 登录失败计数，参数 account */
    LOGIN_FAIL   ("auth:login:fail:%s"),

    /** 账号锁定，参数 account */
    LOGIN_LOCK   ("auth:login:lock:%s"),

    /** Token 注销黑名单（Set，无参数） */
    LOGOUT_TOKEN ("auth:logout:token"),
    ;

    private final String pattern;

    AuthRedisKey(String pattern) {
        this.pattern = pattern;
    }

    public String build(Object... args) {
        return String.format(this.pattern, args);
    }
}
