package com.chbcraft.deploy.client;

import com.chbcraft.deploy.config.QqBotProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QqFeignTokenConfig 单元测试 — 验证 Bearer 鉴权头的附加与空 token 防御
 */
class QqFeignTokenConfigTest {

    @Test
    void 拦截器应为请求附加Bearer鉴权头() {
        RequestInterceptor interceptor = new QqFeignTokenConfig()
                .bearerAuthInterceptor(new QqBotProperties("http://localhost:3000", "SECRET"));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertTrue(template.headers().containsKey("Authorization"));
        assertEquals("Bearer SECRET", template.headers().get("Authorization").iterator().next());
    }

    @Test
    void token未配置时不应附加Authorization头() {
        RequestInterceptor interceptor = new QqFeignTokenConfig()
                .bearerAuthInterceptor(new QqBotProperties("http://localhost:3000", ""));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertFalse(template.headers().containsKey("Authorization"));
    }
}
