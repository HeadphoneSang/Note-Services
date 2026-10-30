package com.chbcraft.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * 网关统一跨域配置。
 *
 * <p>网关是浏览器唯一直连的入口（note/auth 经 lb:// 转发，不直连浏览器），
 * 故跨域响应头只在此处下发一处，下游服务不再各自加 CORS，避免
 * Access-Control-Allow-Origin 重复导致浏览器拒绝。
 *
 * <p>注意：开启 allowCredentials 时，来源必须用 allowedOriginPatterns 而非
 * allowedOrigins("*")——后者与凭证组合会被浏览器判为非法。patterns 会把 "*"
 * 解析成请求实际来源回显，从而与凭证兼容。
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许的来源（带凭证场景下用 patterns，回显具体 origin，兼容 allowCredentials）
        config.addAllowedOriginPattern("*");
        // 允许携带 Cookie / 凭证
        config.setAllowCredentials(true);
        // 允许所有请求头与方法
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        // 预检请求缓存时间（秒）
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
