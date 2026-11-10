package com.chbcraft.gateway.filter;

import com.chcbcraft.dto.enums.AuthRedisKey;
import com.chbcraft.gateway.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 全局 JWT 过滤器
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalJwtFilter implements GlobalFilter {

    private final JwtUtils jwtUtils;

    private final StringRedisTemplate stringRedisTemplate;

    /** Redis 中存储已注销 token 的 Set 的 key（与 auth 服务 logout 接口同一口径） */
    private static final String LOGOUT_TOKEN_SET_KEY = AuthRedisKey.LOGOUT_TOKEN.build();

    @Autowired
    public GlobalJwtFilter(JwtUtils jwtUtils, StringRedisTemplate stringRedisTemplate){
        this.jwtUtils = jwtUtils;
        this.stringRedisTemplate = stringRedisTemplate;
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

        // 2. 获取 Authorization 请求头
        String authHeader = request.getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null ||
                !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }

        // 3. 提取 Token
        String token = authHeader.substring(7).trim();

        if (token.isEmpty()) {
            return unauthorized(exchange);
        }

        try {
            // 校验 Token 签名是否合法
            if (!jwtUtils.validateToken(token)) {
                return writeError(exchange.getResponse(), "无效的认证令牌");
            }
            // 检查 token 是否已被登出（Redis 黑名单）
            Boolean isLoggedOut = stringRedisTemplate.opsForSet().isMember(LOGOUT_TOKEN_SET_KEY, token);
            if (Boolean.TRUE.equals(isLoggedOut)) {
                return isLogout(exchange);
            }
            // 检查 Token 是否过期
            if (jwtUtils.isTokenExpired(token)) {
                return writeError(exchange.getResponse(), "令牌已过期，请重新登录");
            }
            // 检查

            // 4. 解析
            String userId = String.valueOf(jwtUtils.getUserIdFromToken(token));
            String account = jwtUtils.getAccountFromToken(token);

            // 4.1 检查用户是否被禁用
            String lockKey = AuthRedisKey.LOGIN_LOCK.build(account);

            // 3. 检查账号是否被锁定
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey))) {
                long ttl = stringRedisTemplate.getExpire(lockKey, TimeUnit.MINUTES);
                throw new IllegalArgumentException("账号已被临时锁定，请 " + ttl + " 分钟后再试");
            }

            // 5. 删除客户端可能伪造的身份请求头
            ServerHttpRequest newRequest = request.mutate()
                    .headers(headers -> {
                        headers.remove("X-User-Id");
                        headers.set("X-User-Id", userId);
                        headers.remove("X-User-Account");
                        headers.set("X-User-Account", account);
                    })
                    .build();

            // 6. 将修改后的请求传递给下游服务
            ServerWebExchange newExchange = exchange.mutate()
                    .request(newRequest)
                    .build();

            return chain.filter(newExchange);

        } catch (Exception e) {
            // Token 无效、过期、签名错误等
            return unauthorized(exchange);
        }
    }
    /**
     * 统一返回 401 错误
     */
    private Mono<Void> writeError(ServerHttpResponse response,
                                  String message) {

        response.setStatusCode(HttpStatus.UNAUTHORIZED);

        response.getHeaders().setContentType(
                MediaType.APPLICATION_JSON
        );

        String result = "{\"code\":401,\"msg\":\"" + message + "\"}";

        DataBuffer buffer = response.bufferFactory()
                .wrap(result.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }
    /**
     * 判断是否属于白名单
     */
    private boolean isWhiteList(String path) {
        return WHITE_LIST.contains(path);
    }

    private Mono<Void> isLogout(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String result = "{\"code\":401,\"msg\":\"令牌已失效，请重新登录\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(result.getBytes(StandardCharsets.UTF_8));
        // 写入响应并结束请求
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * 返回 401
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(
                HttpStatus.UNAUTHORIZED
        );

        return exchange.getResponse().setComplete();
    }

}
