package com.chbcraft.authCommon.exceptions;

import com.chcbcraft.dto.vo.Result;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Collections;
import java.util.Map;

/**
 * 未授权异常（HTTP 401）— 携带机器可读错误码 {@link AuthError} 与可选结构化数据。
 * <p>
 * message 仅承载用户可读文案（默认取 {@link AuthError#getDefaultMsg()}，可自定义覆盖）；
 * 前端分流依据是 err 枚举名（经 {@code Result.err} 下发），而非 message 文案，
 * 因此文案可随意调整、本地化，不影响客户端协议。
 *
 * @see AuthError
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {

    private final AuthError err;

    /** 结构化附加数据（如 ACCOUNT_LOCKED 的 minutes），随 Result.data 下发，可为 null */
    private final Map<String, Object> detail;

    public UnauthorizedException(AuthError err) {
        this(err, err.getDefaultMsg(), null);
    }

    public UnauthorizedException(AuthError err, Map<String, Object> detail) {
        this(err, err.getDefaultMsg(), detail);
    }

    /** 使用自定义用户文案覆盖 {@link AuthError#getDefaultMsg()} */
    public UnauthorizedException(AuthError err, String message) {
        this(err, message, null);
    }

    public UnauthorizedException(AuthError err, String message, Map<String, Object> detail) {
        super(message);
        this.err = err;
        this.detail = detail;
    }

    public AuthError getErr() {
        return err;
    }

    /** 只读视图；未设置附加数据时返回 null */
    public Map<String, Object> getDetail() {
        return detail == null ? null : Collections.unmodifiableMap(detail);
    }

    /**
     * 转为 401 统一响应：{@code err=枚举名，message=文案，data=detail}。
     * 供异常处理器与网关过滤器直接下发，避免各处手工拼装字段。
     */
    public Result<Object> toResult() {
        return Result.unauthorized(err.name(), getMessage(), detail);
    }
}
