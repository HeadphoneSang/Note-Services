package com.chbcraft.auth.interceptor;

import com.chcbcraft.dto.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 用户上下文拦截器 — 将网关注入的用户身份头写入 {@link UserContext}
 * <p>
 * 网关（GlobalJwtFilter）校验 JWT 后注入 X-User-Id / X-User-Account，
 * 并删除客户端伪造的同名头，因此下游可信任这两个头。
 * afterCompletion 无条件清理 ThreadLocal，防止 Tomcat 线程复用串用户。
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String ACCOUNT_HEADER = "X-User-Account";

    private static final String UNAUTHORIZED_BODY =
            "{\"code\":401,\"msg\":\"未登录或认证信息无效\"}";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        // 放行 OPTIONS 请求（CORS 预检），不写入上下文
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String userIdStr = request.getHeader(USER_ID_HEADER);
        if (userIdStr == null || userIdStr.isBlank()) {
            return reject(response);
        }
        Integer userId;
        try {
            userId = Integer.valueOf(userIdStr.trim());
        } catch (NumberFormatException e) {
            return reject(response);
        }

        UserContext.set(userId, request.getHeader(ACCOUNT_HEADER));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        // 无论请求成功失败都清理，防止线程池复用串用户
        UserContext.clear();
    }

    private boolean reject(HttpServletResponse response) throws Exception {
        response.setContentType("application/json;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(UNAUTHORIZED_BODY);
        return false;
    }
}
