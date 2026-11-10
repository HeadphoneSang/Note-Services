package com.chbcraft.auth.util;

import com.chcbcraft.dto.enums.TokenType;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类 — 生成 & 校验 Token
 * <p>
 * 密钥与过期时间从配置读取（Nacos jwt.properties / GATEWAY_GROUP，与网关共享），
 * 默认值兼容拆分前 note-module 硬编码的密钥，保证存量 token 继续有效。
 */
@Component
public class JwtUtil {

    private final SecretKey secretKey;

    @Getter
    private final long expiration;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /**
     * 获取 Token 过期时间（毫秒）
     */
    public long getExpirationTime() {
        return expiration;
    }

    /**
     * 生成 token
     *
     * @param userId 用户 ID
     * @param account 用户账号
     * @return JWT token 字符串
     */
    public String generateAccToken(int userId, String account,String sessionId) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expiration);
        Map<String,String> claims = new HashMap<>();
        claims.put("sessionId", sessionId);
        claims.put("tokenType", TokenType.ACCESS_TOKEN.getType());
        return Jwts.builder()
                .setClaims(claims)
                .setId(String.valueOf(userId))
                .setSubject(account)
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 生成的 Token 是一个随机字符串，具有 256 bit 的随机熵。
     * @return 生成的 Token 是一个随机字符串，具有 256 bit 的随机熵。
     */
    public String generateRefreshToken() {
        byte[] randomBytes = new byte[32];

        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    public String generateToken(int userId, String account, String sessionId, TokenType type){
        String token;
        if(type==TokenType.ACCESS_TOKEN){
            token = generateAccToken(userId, account, sessionId);
        }else if (type==TokenType.REFRESH_TOKEN){
            token = generateRefreshToken();
        }else{
            throw new IllegalArgumentException("Invalid token type");
        }
        return token;
    }

    /**
     * 校验 token 是否有效（格式 + 签名 + 过期）
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 从 token 中提取用户 ID，无效返回 0
     */
    public int getUserIdFromToken(String token) {
        try {
            Claims claims = getClaims(token);
            return Integer.parseInt(claims.getId());
        } catch (JwtException | IllegalArgumentException e) {
            return 0;
        }
    }

    /**
     * 从 token 中提取账号，无效返回 null
     */
    public String getAccountFromToken(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 判断 token 是否已过期（无效 token 视为已过期）
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }

    /**
     * 解析 token，获取 Claims
     */
    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String getSessionId(String token){
        return getClaims(token).get("sessionId", String.class);
    }

}
