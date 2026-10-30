package com.chbcraft.auth.config;

import com.chbcraft.auth.interceptor.UserContextInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private UserContextInterceptor userContextInterceptor;

    // 跨域由网关统一处理（002-gateway-cors），下游不再配置 CORS，避免重复 ACAO 头

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // auth 服务中仅改密码接口需要登录态（其余为白名单接口或自帶 token 校验）
        registry.addInterceptor(userContextInterceptor)
                .addPathPatterns("/user/password");
    }
}
