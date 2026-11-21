package com.chbcraft.authCommon.exceptions;

/**
 * 认证错误码枚举 — 前端分流逻辑的唯一事实源。
 * <p>
 * 401 响应中经 {@code Result.err} 字段下发（序列化为 {@code enum.name()}），
 * 前端<b>按 err 值 switch 分流，不得解析 message 文案</b>（message 仅用于直接展示）。
 * <p>
 * 结构化附加参数经 {@link UnauthorizedException#getDetail()} 随 {@code Result.data} 下发，
 * 如 {@code ACCOUNT_LOCKED} 携带 {@code {"minutes": 2}}（-1 表示永久锁定）。
 * <p>
 * 前端侧应维护同名的常量定义（如 TS 的 union type / const 对象），
 * 两侧均禁止书写裸字符串字面量。
 */
public enum AuthError {

    /** 未携带凭证或凭证缺失（缺 Authorization 头 / 非 Bearer 前缀 / 关键参数为空） */
    UNAUTHORIZED("未登录或凭证缺失"),  // 要求重新登录

    /** AccessToken 解析失败（token 为空或其他非法情况，IllegalArgumentException） */
    TOKEN_ERROR("登录凭证异常，请重新登录"),  // 要求重新登录

    /** AccessToken 已过期，客户端应改走 RefreshToken 刷新 */
    EXPIRED_NEED_REFRESH("登录已过期，请刷新后重试"), // 无感知自动触发刷新access token

    /** AccessToken 格式不合法（MalformedJwtException） */
    INVALID_TOKEN("无效的登录凭证，请重新登录"), // 要求重新登录

    /** 会话已被登出或长会话超时（Redis 会话 ZSET 中不存在） */
    TOKEN_INVALIDATED("登录状态已失效，请重新登录"), // 要求重新登录

    /** 账号被锁定（登录失败次数达上限），data 携带 minutes：剩余锁定分钟，-1 为永久锁定 */
    ACCOUNT_LOCKED("账号已被锁定，请稍后再试"), // 要求重新登录

    /** RefreshToken 在 Redis 中不存在（已过期或从未签发），只能重新登录 */
    REFRESH_TOKEN_INVALID("登录已过期，请重新登录"),  // 要求重新登录

    /** RefreshToken 重放（已被使用过），对应会话已被服务端销毁，必须重新登录 */
    REFRESH_TOKEN_REPLAYED("检测到会话异常，请重新登录"), // 要求重新登录

    /** 凭证合法但用户已不存在（如被删除） */
    USER_NOT_FOUND("用户不存在，请重新登录"), // 要求重新登录

    /** 鉴权兜底失败（Redis 不可用、验签异常等未归类错误） */
    AUTH_FAILED("认证失败，请重新登录"); // 要求重新登录

    private final String defaultMsg;

    AuthError(String defaultMsg) {
        this.defaultMsg = defaultMsg;
    }

    /**
     * 默认用户文案（中文，可直接展示）。
     * 异常构造时可传入自定义文案覆盖（如 {@code ACCOUNT_LOCKED} 附带具体分钟数）。
     */
    public String getDefaultMsg() {
        return defaultMsg;
    }
}
