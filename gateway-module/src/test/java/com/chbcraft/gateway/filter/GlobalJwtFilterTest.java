package com.chbcraft.gateway.filter;

import com.chcbcraft.gateway.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GlobalJwtFilter 单元测试 — 白名单放行 / 黑名单拦截 / 身份头注入
 */
@ExtendWith(MockitoExtension.class)
class GlobalJwtFilterTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private SetOperations<String, String> setOps;

    @Mock
    private GatewayFilterChain chain;

    private GlobalJwtFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GlobalJwtFilter(jwtUtils, redis);
    }

    private MockServerWebExchange exchange(String method, String path, String bearer) {
        MockServerHttpRequest.BaseBuilder<?> builder =
                MockServerHttpRequest.method(HttpMethod.valueOf(method), path);
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return MockServerWebExchange.from(builder.build());
    }

    @Test
    void whiteListPathPassesWithoutToken() {
        MockServerWebExchange exchange = exchange("POST", "/api/auth/user/login", null);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        // 白名单直接放行，不注入身份头
        assertNull(exchange.getRequest().getHeaders().getFirst("X-User-Id"));
        verify(chain).filter(any());
    }

    @Test
    void missingTokenIsUnauthorized() {
        MockServerWebExchange exchange = exchange("GET", "/api/note/user/info", null);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void loggedOutTokenIsRejected() {
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.isMember(anyString(), eq("tok"))).thenReturn(true);

        MockServerWebExchange exchange = exchange("GET", "/api/note/user/info", "tok");

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void validTokenInjectsUserIdHeader() {
        when(jwtUtils.validateToken("tok")).thenReturn(true);
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.isMember(anyString(), eq("tok"))).thenReturn(false);
        when(jwtUtils.isTokenExpired("tok")).thenReturn(false);
        when(jwtUtils.getUserIdFromToken("tok")).thenReturn(42);
        when(jwtUtils.getAccountFromToken("tok")).thenReturn("alice");
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exchange = exchange("GET", "/api/note/user/info", "tok");

        filter.filter(exchange, chain).block();

        // 网关把解析出的身份注入请求头（下游 UserContextInterceptor 读取）
        assertEquals("42", exchange.getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("alice", exchange.getRequest().getHeaders().getFirst("X-User-Account"));
        verify(chain).filter(any());
    }
}
