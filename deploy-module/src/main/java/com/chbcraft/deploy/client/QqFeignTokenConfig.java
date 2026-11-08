package com.chbcraft.deploy.client;

import com.chbcraft.deploy.config.QqBotProperties;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;

/**
 * QqMessageClient 专属 Feign 配置 — 为每个请求附加 Bearer 鉴权头。
 * <p>
 * 注意：故意不加 @Configuration，避免被全局组件扫描后波及所有 Feign 客户端；
 * 仅通过 @FeignClient(configuration = ...) 引入，作用域限本客户端。
 */
public class QqFeignTokenConfig {

    @Bean
    public RequestInterceptor bearerAuthInterceptor(QqBotProperties properties) {
        return template -> {
            String token = properties.getToken();
            // 未配置 token 时不附加头，避免拼出 "Bearer null" 这类脏值
            if (token != null && !token.isBlank()) {
                template.header("Authorization", "Bearer " + token);
            }
        };
    }
}
