package com.chbcraft.gateway.filter;

import com.chbcraft.auth.util.JwtUtil;
import com.chbcraft.auth.util.RedisAuthUtil;
import com.chcbcraft.dto.enums.AuthRedisKey;
import com.chcbcraft.dto.vo.Result;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.List;

/**
 * 全局 JWT 过滤器
 * <p>
 * 鉴权失败统一经 {@link #writeResult} 以 {@link Result} JSON 体返回
 * （与各业务模块 Controller 的响应结构、字段名保持一致），
 * 不再手拼 JSON 字符串或返回无 body 的裸 401。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalJwtFilter implements GlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(GlobalJwtFilter.class);

    private final JwtUtil jwtUtil;

    private final ObjectMapper objectMapper;

    private final RedisAuthUtil redisAuthUtil;

    @Autowired
    public GlobalJwtFilter(JwtUtil jwtUtil,
                           ObjectMapper objectMapper,
                           RedisAuthUtil redisAuthUtil) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.redisAuthUtil = redisAuthUtil;
    }

    // 不需要鉴权的接口（均属 auth 服务）
    private static final List<String> WHITE_LIST = List.of(
            "/api/auth/user/login",
            "/api/auth/user/refresh",
            "/api/auth/user/register",
            "/api/auth/user/send-email-code",
            "/api/auth/user/email-login",
            "/api/auth/user/token-info",
            "/api/auth/captcha"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();

        //  放行 OPTIONS 请求（CORS 预检）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod().toString())) {
            return chain.filter(exchange);
        }

        String path = request.getURI().getPath();

        // 1. 白名单放行
        if (isWhiteList(path)) {
            return chain.filter(exchange);
        }

        // 获取 Authorization 请求头
        String authHeader = request.getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null ||
                !authHeader.startsWith("Bearer ")) {
            return writeResult(exchange, Result.unauthorized("未携带认证令牌"));
        }
        // 提取 Token
        String token = jwtUtil.extractToken(authHeader);
        try {
            // 验证
            try {
                validateAccessToken(token);
            } catch (IllegalArgumentException e) {
                return writeResult(exchange, Result.unauthorized(e.getMessage()));
            }
            // 删除客户端可能伪造的身份请求头
            String userId = String.valueOf(jwtUtil.getUserIdFromToken(token));
            String account = jwtUtil.getAccountFromToken(token);
            ServerHttpRequest newRequest = request.mutate()
                    .headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.set("X-User-Id", userId);
                        headers.remove("X-User-Account");
                        headers.set("X-User-Account", account);
                    })
                    .build();
            // 将修改后的请求传递给下游服务
            ServerWebExchange newExchange = exchange.mutate()
                    .request(newRequest)
                    .build();

            return chain.filter(newExchange);
        }
        catch (Exception e) {
            // Redis 不可用、解析异常等兜底
            log.warn("JWT 鉴权异常: {}", e.getMessage());
            return writeResult(exchange, Result.unauthorized("认证失败，请重新登录"));
        }
    }

    public void validateAccessToken(String accessToken) {
        // 校验 Token 签名与有效期
        jwtUtil.validateToken(accessToken);
        // 解析
        String userId = String.valueOf(jwtUtil.getUserIdFromToken(accessToken));
        String account = jwtUtil.getAccountFromToken(accessToken);
        // 检查用户是否已经被登出
        String sessionId = jwtUtil.getSessionId(accessToken);
        // 懒过滤，删除SET中的小于等于当前时间戳的成员，然后返回是否有sessionId对应的
        String sessionKey = AuthRedisKey.USER_SESSION_SET.build(userId);
        redisAuthUtil.validLogoutOrExpire(sessionKey, sessionId);
        // 检查账号是否被锁定（文案直接返回给客户端，不再经异常中转被吞掉）
        String lockKey = AuthRedisKey.LOGIN_LOCK.build(account);
        redisAuthUtil.validLockTTL(lockKey);
    }

    /**
     * 统一把 {@link Result} 序列化为 JSON 写入响应（等价于 Controller 返回 Result 的效果）。
     * <p>
     * 401/403/404/500 映射为真实 HTTP 状态码（浏览器/前端按状态码感知鉴权失败），
     * 其余（含 200 与未知 code）返回 HTTP 200、错误码放 body —— 与业务模块 Controller 行为一致。
     */
    private Mono<Void> writeResult(ServerWebExchange exchange, Result<?> result) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatusOf(result.getCode()));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(result);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (JsonProcessingException e) {
            log.error("Result 序列化失败: {}", result, e);
            return response.setComplete();
        }
    }

    private static HttpStatus httpStatusOf(int code) {
        return switch (code) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 500 -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> HttpStatus.OK;
        };
    }

    /**
     * 判断是否属于白名单
     */
    private boolean isWhiteList(String path) {
        return WHITE_LIST.contains(path);
    }

}
