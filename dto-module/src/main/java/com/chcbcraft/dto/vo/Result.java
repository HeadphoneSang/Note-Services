package com.chcbcraft.dto.vo;

/**
 * API 统一响应类
 * 所有接口统一返回此格式
 */
public class Result<T> {

    private int code;       // 状态码：200成功，400参数错误，401未授权，500服务器错误
    private String err;     // 机器可读业务错误码（如 EXPIRED_NEED_REFRESH），前端分流依据；成功时为 null。勿解析 message
    private String message;     // 提示信息（仅用于直接展示给用户）
    private T data;         // 返回数据

    private Result() {}

    private Result(int code, String msg, T data) {
        this(code, null, msg, data);
    }

    private Result(int code, String err, String msg, T data) {
        this.code = code;
        this.err = err;
        this.message = msg;
        this.data = data;
    }

    // ============ 成功的快捷方法 ============

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(200, msg, data);
    }

    // ============ 失败的快捷方法 ============

    public static <T> Result<T> error(String msg) {
        return new Result<>(400, msg, null);
    }

    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    public static <T> Result<T> error(int code,String msg, T data) {
        return new Result<>(code, msg, data);
    }

    /** 无业务错误码的 401（兼容保留），新代码请用 {@link #unauthorized(String, String, Object)} */
    public static <T> Result<T> unauthorized(String msg) {
        return unauthorized(null, msg, null);
    }

    /**
     * 401 + 机器可读业务错误码（如 AuthError 枚举名）+ 用户文案 + 结构化数据。
     * 前端按 err 分流处理，message 仅作展示。
     */
    public static <T> Result<T> unauthorized(String err, String msg, T data) {
        return new Result<>(401, err, msg, data);
    }

    public static <T> Result<T> serverError() {
        return new Result<>(500, "服务器内部错误", null);
    }

    // ============ Getter / Setter ============

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getErr() { return err; }
    public void setErr(String err) { this.err = err; }

    public String getMessage() { return message; }
    public void setMessage(String msg) { this.message = msg; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
