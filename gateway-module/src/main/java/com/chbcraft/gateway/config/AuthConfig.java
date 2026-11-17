package com.chbcraft.gateway.config;

import com.chbcraft.auth.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthConfig {
    @Bean
    public JwtUtil jwtUtil(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expiration, @Value("${jwt.access.expiration}") long accessExpiration, @Value("${jwt.refresh.expiration}") long refreshExpiration, @Value("${jwt.session.limit}") int sessionLimit, @Value("${jwt.login.failure.times}") int maxLoginAttempts, @Value("${jwt.login.failure.lockDuration}") int lockDuration){
        return new JwtUtil(secret, expiration, accessExpiration, refreshExpiration, sessionLimit, maxLoginAttempts, lockDuration);
    }

}
