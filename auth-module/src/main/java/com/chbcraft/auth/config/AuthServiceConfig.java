package com.chbcraft.auth.config;

import com.chbcraft.authCommon.services.JwtService;
import com.chbcraft.authCommon.services.RedisAuthService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class AuthServiceConfig {


    @Bean
    public JwtService authService(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expiration, @Value("${jwt.access.expiration}") long accessExpiration, @Value("${jwt.refresh.expiration}") long refreshExpiration, @Value("${jwt.session.limit}") int sessionLimit, @Value("${jwt.login.failure.times}") int maxLoginAttempts, @Value("${jwt.login.failure.lockDuration}") int lockDuration
    ) {
        return new JwtService(secret, expiration, accessExpiration, refreshExpiration, sessionLimit, maxLoginAttempts, lockDuration);
    }

    @Bean
    public RedisAuthService redisAuthService(StringRedisTemplate stringRedisTemplate, @Qualifier("expireAndCheck") RedisScript<Long> expireAndCheckScript) {
        return new RedisAuthService(stringRedisTemplate, expireAndCheckScript);
    }
}
