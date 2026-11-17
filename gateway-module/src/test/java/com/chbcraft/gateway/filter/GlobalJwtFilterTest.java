package com.chbcraft.gateway.filter;

import com.chbcraft.auth.util.JwtUtil;
import com.chcbcraft.dto.enums.AuthRedisKey;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 网关全局 JWT 过滤器的单元测试。
 *
 * <p>不起 Spring 容器（避免拉起 nacos/redis 配置），JwtUtil 与 StringRedisTemplate
 * 用 Mockito 替身，请求/响应用 MockServerWebExchange。
 * 重点验证：鉴权失败统一返回 {@code Result} JSON（code/message 字段），
 * 账号锁定文案直达客户端，有效 token 透传并注入可信身份头。
 */
@ExtendWith(MockitoExtension.class)
class GlobalJwtFilterTest {

    private static final String PROTECTED_URI = "http://gw.local/api/note/list";
    private static final String TOKEN = "fake.jwt.token";
    private static final String LOGOUT_KEY = AuthRedisKey.LOGOUT_TOKEN.build();
    private static final String LOCK_KEY = AuthRedisKey.LOGIN_LOCK.build("sang");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    private GlobalJwtFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GlobalJwtFilter(jwtUtil, stringRedisTemplate, MAPPER);
    }

    // ============ 放行路径 ============

    @Test
    @DisplayName("OPTIONS 预检请求：直接放行不鉴权")
    void optionsShouldPassThrough() {
        AtomicReference<MockServerWebExchange> passed = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            passed.set((MockServerWebExchange) ex);
            return Mono.empty();
        };
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options(PROTECTED_URI).build());

        filter.filter(exchange, chain).block();

        assertThat(passed.get()).isSameAs(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("白名单接口（登录）：直接放行不鉴权")
    void whiteListShouldPassThrough() {
        AtomicReference<MockServerWebExchange> passed = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            passed.set((MockServerWebExchange) ex);
            return Mono.empty();
        };
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("http://gw.local/api/auth/user/login").build());

        filter.filter(exchange, chain).block();

        assertThat(passed.get()).isSameAs(exchange);
    }

    // ============ 401 + Result JSON body ============

    @Test
    @DisplayName("缺失 Authorization 头：HTTP 401，body 为 Result JSON（code=401, message=未携带认证令牌，不再是无 body 裸 401）")
    void missingHeaderShouldReturn401ResultJson() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(PROTECTED_URI).build());

        filter.filter(exchange, chainNotReached()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(org.springframework.http.MediaType.APPLICATION_JSON);
        Map<String, Object> body = bodyJson(exchange);
        assertThat(body.get("code")).isEqualTo(401);
        assertThat(body.get("message")).isEqualTo("未携带认证令牌");
        // 旧手拼 JSON 的字段名是 msg，统一为 Result 后必须退场
        assertThat(body).doesNotContainKey("msg");
    }

    @Test
    @DisplayName("Bearer 后 token 为空：HTTP 401，message=认证令牌为空")
    void emptyTokenShouldReturn401() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(PROTECTED_URI)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer ").build());

        filter.filter(exchange, chainNotReached()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyJson(exchange).get("message")).isEqualTo("认证令牌为空");
    }

    @Test
    @DisplayName("validateToken 抛 expired_need_refresh：HTTP 401，message=令牌已过期（区分过期与无效，供前端走 refresh 流程）")
    void expiredTokenShouldReturnDistinctMessage() throws Exception {
        when(jwtUtil.validateToken(TOKEN))
                .thenThrow(new IllegalArgumentException("expired_need_refresh"));
        MockServerWebExchange exchange = authorizedExchange();

        filter.filter(exchange, chainNotReached()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyJson(exchange).get("message")).isEqualTo("令牌已过期，请重新登录");
    }

    @Test
    @DisplayName("validateToken 抛 invalid_token：HTTP 401，message=无效的认证令牌")
    void invalidTokenShouldReturnDistinctMessage() throws Exception {
        when(jwtUtil.validateToken(TOKEN))
                .thenThrow(new IllegalArgumentException("invalid_token"));
        MockServerWebExchange exchange = authorizedExchange();

        filter.filter(exchange, chainNotReached()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyJson(exchange).get("message")).isEqualTo("无效的认证令牌");
    }

    @Test
    @DisplayName("token 已在 logout 黑名单：HTTP 401，message=令牌已失效")
    void loggedOutTokenShouldReturn401() throws Exception {
        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(LOGOUT_KEY, TOKEN)).thenReturn(true);
        MockServerWebExchange exchange = authorizedExchange();

        filter.filter(exchange, chainNotReached()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bodyJson(exchange).get("message")).isEqualTo("令牌已失效，请重新登录");
    }

    // ============ 账号锁定：403 文案直达（回归：此前被 catch 吞掉变裸 401） ============

    @Test
    @DisplayName("临时锁定（ttl=119s）：HTTP 403，message 含剩余分钟数（119s → 2 分钟）")
    void tempLockedShouldReturn403WithMinutes() throws Exception {
        stubValidToken();
        when(stringRedisTemplate.getExpire(LOCK_KEY, TimeUnit.SECONDS)).thenReturn(119L);
        MockServerWebExchange exchange = authorizedExchange();

        filter.filter(exchange, chainNotReached()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        Map<String, Object> body = bodyJson(exchange);
        assertThat(body.get("code")).isEqualTo(403);
        assertThat(body.get("message")).isEqualTo("账号已被临时锁定，请 2 分钟后再试");
    }

    @Test
    @DisplayName("永久锁定（ttl=-1）：HTTP 403，message=账号已永久锁定")
    void permanentlyLockedShouldReturn403() throws Exception {
        stubValidToken();
        when(stringRedisTemplate.getExpire(LOCK_KEY, TimeUnit.SECONDS)).thenReturn(-1L);
        MockServerWebExchange exchange = authorizedExchange();

        filter.filter(exchange, chainNotReached()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(bodyJson(exchange).get("message")).isEqualTo("账号已永久锁定");
    }

    // ============ 有效 token 透传 ============

    @Test
    @DisplayName("有效 token：放行下游，且客户端伪造的 X-User-Id 被服务端解析值覆盖")
    void validTokenShouldPassWithTrustedHeaders() {
        stubValidToken();
        when(stringRedisTemplate.getExpire(LOCK_KEY, TimeUnit.SECONDS)).thenReturn(-2L);
        AtomicReference<org.springframework.web.server.ServerWebExchange> passed = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            passed.set(ex);
            return Mono.empty();
        };
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(PROTECTED_URI)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .header("X-User-Id", "999")           // 客户端伪造
                        .header("X-User-Account", "hacker")   // 客户端伪造
                        .build());

        filter.filter(exchange, chain).block();

        assertThat(passed.get()).isNotNull();
        HttpHeaders forwarded = passed.get().getRequest().getHeaders();
        assertThat(forwarded.getFirst("X-User-Id")).isEqualTo("42");
        assertThat(forwarded.getFirst("X-User-Account")).isEqualTo("sang");
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ============ 辅助 ============

    /** 伪造一个已通过校验的 token 环境（userId=42, account=sang，未登出、未过期） */
    private void stubValidToken() {
        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.isMember(LOGOUT_KEY, TOKEN)).thenReturn(false);
        when(jwtUtil.isTokenExpired(TOKEN)).thenReturn(false);
        when(jwtUtil.getUserIdFromToken(TOKEN)).thenReturn(42);
        when(jwtUtil.getAccountFromToken(TOKEN)).thenReturn("sang");
    }

    private MockServerWebExchange authorizedExchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(PROTECTED_URI)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .build());
    }

    /** 鉴权失败时不应触达下游：链一旦被调即让断言失败 */
    private GatewayFilterChain chainNotReached() {
        return ex -> {
            throw new AssertionError("鉴权失败不应转发下游: " + ex.getRequest().getURI());
        };
    }

    private Map<String, Object> bodyJson(MockServerWebExchange exchange) throws Exception {
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).isNotNull();
        return MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {});
    }
}
