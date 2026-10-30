package com.chbcraft.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关单一权威 CORS 的单元测试。
 *
 * <p>不起 Spring 容器（避免拉起 nacos/redis 配置），直接实例化 {@link CorsConfig}
 * 验证 {@link CorsWebFilter} 的跨域行为。
 */
class CorsConfigTest {

    private static final String ORIGIN = "http://localhost:5173";

    private CorsWebFilter corsWebFilter;

    @BeforeEach
    void setUp() {
        corsWebFilter = new CorsConfig().corsWebFilter();
    }

    @Test
    @DisplayName("预检 OPTIONS：回显具体 Origin（非 *）并放行凭证/方法/请求头，预检直接应答不转发下游")
    void preflightShouldEchoOriginAndAllowCredentials() {
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        MockServerHttpRequest request = MockServerHttpRequest
                .options("http://gw.local/api/note/list")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        corsWebFilter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        // 开启凭证时必须回显具体 Origin，绝不能是 "*"（浏览器对 *+credentials 判非法）
        assertThat(headers.getAccessControlAllowOrigin()).isEqualTo(ORIGIN);
        assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)).contains("POST");
        assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).toLowerCase())
                .contains("authorization");
        assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_MAX_AGE)).isEqualTo("3600");
        // 预检由 CORS 处理器直接应答，不应进入后续过滤链
        assertThat(chainCalled).isFalse();
    }

    @Test
    @DisplayName("实际请求 GET（带 Origin）：响应携带 ACAO/ACAC，并继续走过滤链")
    void actualRequestShouldCarryCorsHeadersAndContinueChain() {
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://gw.local/api/note/list")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        corsWebFilter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getAccessControlAllowOrigin()).isEqualTo(ORIGIN);
        assertThat(headers.getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        assertThat(chainCalled).isTrue();
    }

    @Test
    @DisplayName("无 Origin 的普通请求：不加任何 CORS 头，直接放行")
    void nonCorsRequestShouldPassThroughUntouched() {
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://gw.local/api/note/list")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        corsWebFilter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getAccessControlAllowOrigin()).isNull();
        assertThat(chainCalled).isTrue();
    }
}
