package com.chcbcraft.dto.context;

/**
 * 用户上下文 — 基于 ThreadLocal 存储当前请求的用户信息
 * <p>
 * 由各服务的拦截器在 preHandle 中写入（来源：网关校验 JWT 后注入的
 * X-User-Id / X-User-Account 请求头），Controller / Service 随处可读。
 * <p>
 * 注意：必须由拦截器在 afterCompletion 中调用 {@link #clear()}，
 * 否则 Tomcat 线程池复用会把上一个请求的用户身份带给下一个请求。
 */
public final class UserContext {

    private static final ThreadLocal<Integer> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ACCOUNT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Integer userId, String account) {
        USER_ID.set(userId);
        ACCOUNT.set(account);
    }

    /** 当前用户 ID，未登录返回 null */
    public static Integer getUserId() {
        return USER_ID.get();
    }

    /** 当前用户账号，未登录返回 null */
    public static String getAccount() {
        return ACCOUNT.get();
    }

    /** 清理上下文（拦截器 afterCompletion 必须无条件调用） */
    public static void clear() {
        USER_ID.remove();
        ACCOUNT.remove();
    }
}
