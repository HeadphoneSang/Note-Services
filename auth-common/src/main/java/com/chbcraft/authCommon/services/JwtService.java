package com.chbcraft.authCommon.services;


import com.chbcraft.authCommon.exceptions.AuthError;
import com.chbcraft.authCommon.exceptions.UnauthorizedException;
import com.chcbcraft.dto.enums.TokenType;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证域 JWT 服务 — access/refresh 双 Token 的签发、解析与校验唯一入口。
 * <p>
 * auth 服务（UserService 登录/刷新流程签发双 Token）与 gateway（GlobalJwtFilter 鉴权）共用：
 * 两模块各自经本模块配置类注册同款 bean，保证签发与校验口径完全一致。
 * <p>
 * 双 Token 体系：AccessToken 为 HS256 签名的 JWT（自包含 userId/account/sessionId）；
 * RefreshToken 为 256 bit 随机不透明字符串（凭 Redis 中记录的 SHA-256 摘要核验）。
 * <p>
 * 约定：校验类方法「通过则静默返回，失败即抛 {@link UnauthorizedException}」，
 * 异常携带机器可读错误码 {@link AuthError}（前端按 Result.err 分流）与
 * 用户可读文案（仅作展示，可随意调整，不作为协议）。
 */
public class JwtService {

    private final SecretKey secretKey;

    @Getter
    private final long expiration;

    @Getter
    private final long accessExpiration;

    @Getter
    private final long refreshExpiration;

    @Getter
    private final int sessionLimit;

    @Getter
    private final int maxLoginAttempts;

    @Getter
    private final int lockDuration;

    /**
     * 以 {@code jwt.*} 前缀配置项（Nacos jwt.properties）构造本服务，各单位见参数说明。
     *
     * @param secret           HMAC-SHA 签名密钥原文（经 {@code Keys.hmacShaKeyFor} 转为密钥，
     *                         长度须满足 HS256 规范）
     * @param expiration       Token 基础过期时间，<b>毫秒</b>：用作会话 ZSET 成员过期时间戳的增量，
     *                         以及登出黑名单 Set 的 TTL 基准
     * @param accessExpiration AccessToken 有效期，<b>毫秒</b>
     * @param refreshExpiration RefreshToken 有效期，<b>毫秒</b>：Redis 摘要记录 TTL 的基准
     * @param sessionLimit     同一用户允许并存的最大设备会话数（登录 Lua 脚本 checkAndAdd 据此比较）
     * @param maxLoginAttempts 登录失败锁定阈值：连续失败达到该次数即写锁定 key 锁定账号
     * @param lockDuration     账号锁定时长，<b>分钟</b>：锁定 key 的 TTL（见 {@code AuthRedisKey.LOGIN_LOCK}）
     */
    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration,
            @Value("${jwt.access.expiration}") long accessExpiration,
            @Value("${jwt.refresh.expiration}") long refreshExpiration,
            @Value("${jwt.session.limit}") int sessionLimit,
            @Value("${jwt.login.failure.times}") int maxLoginAttempts,
            @Value("${jwt.login.failure.lockDuration}") int lockDuration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
        this.sessionLimit = sessionLimit;
        this.maxLoginAttempts = maxLoginAttempts;
        this.lockDuration = lockDuration;
    }

    /**
     * 获取 Token 基础过期时间。
     * <p>
     * 调用方：UserService 登录时以「当前时间戳 + 本值」作为会话 ZSET 成员的过期时间戳；
     * UserController 登出时以其作为黑名单 Set 的 TTL。
     *
     * @return 基础过期时间，毫秒
     */
    public long getExpirationTime() {
        return expiration;
    }

    /**
     * 获取 AccessToken 有效期（当前暂无调用方，保留作对称查询口径）。
     *
     * @return AccessToken 有效期，毫秒
     */
    public long getAccessExpirationTime() {
        return accessExpiration;
    }

    /**
     * 获取 RefreshToken 有效期。
     * <p>
     * 调用方：UserService 登录流程以其作为 refresh token 在 Redis 中
     * SHA-256 摘要记录（{@code AuthRedisKey.REFRESH_TOKEN}）的 TTL。
     *
     * @return RefreshToken 有效期，毫秒
     */
    public long getRefreshExpirationTime() {
        return refreshExpiration;
    }

    /**
     * 签发 AccessToken（JWT，HS256 签名）。
     * <p>
     * Claim 结构：自定义 claims 携带 {@code sessionId}（设备会话标识）与
     * {@code tokenType}={@code ACCESS_TOKEN}；JWS Id 存 userId、Subject 存 account，
     * iat/exp 为签发时间与「签发时间 + {@code accessExpiration} 毫秒」。
     *
     * @param userId    用户 ID，写入 JWS Id claim
     * @param account   用户账号，写入 Subject claim
     * @param sessionId 设备会话 ID（登录流程经 Lua 会话门禁后生成的 UUID），
     *                  写入自定义 sessionId claim，供后续 Redis 会话校验使用
     * @return 签名后的 compact JWT 字符串
     */
    public String generateAccToken(int userId, String account,String sessionId) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + accessExpiration);
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
     * 签发 RefreshToken：32 字节（256 bit）SecureRandom 随机熵，Base64 URL-safe 无填充编码。
     * <p>
     * 纯随机不透明字符串 —— 不是 JWT，不含 claim 也不签名；有效性凭 Redis 中
     * {@code AuthRedisKey.REFRESH_TOKEN} 记录的 SHA-256 摘要比对核验。
     *
     * @return URL-safe 的随机 RefreshToken 字符串
     */
    public String generateRefreshToken() {
        byte[] randomBytes = new byte[32];

        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    /**
     * 按类型签发 Token 的统一入口，登录/刷新流程经此产出双 Token 对。
     * <p>
     * {@code ACCESS_TOKEN} 委托 {@link #generateAccToken(int, String, String)}，
     * {@code REFRESH_TOKEN} 委托 {@link #generateRefreshToken()}。
     *
     * @param userId    用户 ID
     * @param account   用户账号
     * @param sessionId 设备会话 ID
     * @param type      Token 类型（ACCESS / REFRESH）
     * @return 生成的 Token 字符串
     * @throws IllegalArgumentException {@code type} 不是两个枚举值之一时抛出，
     *                                  消息为 {@code Invalid token type}
     */
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
     * 校验 AccessToken（JWT）的格式、签名与有效期，通过则静默返回。
     * <p>
     * 经 {@link #getClaims(String)} 解析时完成验签与 exp 检查，JJWT 抛出的异常被统一映射为
     * {@link UnauthorizedException}（网关 GlobalJwtFilter 捕获后以 Result.err 下发客户端）：
     * <ul>
     *   <li>{@link AuthError#TOKEN_ERROR} —— Token 为空或其他非法情况（IllegalArgumentException）；</li>
     *   <li>{@link AuthError#EXPIRED_NEED_REFRESH} —— 已过期，客户端应改走 RefreshToken 刷新；</li>
     *   <li>{@link AuthError#INVALID_TOKEN} —— 格式不合法（MalformedJwtException）。</li>
     * </ul>
     * 注意：签名不匹配（SignatureException）不在捕获之列，会以原始 JJWT 异常向上传播。
     *
     * @param token 待校验的 AccessToken
     * @throws UnauthorizedException Token 非法或已过期时抛出，err 为上述枚举值
     */
    public void validateToken(String token) {
        try {
            getClaims(token);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException(AuthError.TOKEN_ERROR);
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException(AuthError.EXPIRED_NEED_REFRESH);
        } catch (MalformedJwtException e) {
            throw new UnauthorizedException(AuthError.INVALID_TOKEN);
        }
    }

    /**
     * 从 token 提取用户 ID（存于 JWS Id claim 并解析为 int）。
     * <p>
     * 失败抛 {@link UnauthorizedException}，err 与 {@link #validateToken(String)} 相同
     * （{@link AuthError#TOKEN_ERROR} / {@link AuthError#EXPIRED_NEED_REFRESH} / {@link AuthError#INVALID_TOKEN}），
     * <b>并非返回 0</b>。主要调用方：网关 GlobalJwtFilter，鉴权通过后写入下游请求头
     * {@code X-User-Id} 传递身份。
     *
     * @param token 已通过校验的 AccessToken
     * @return 用户 ID
     * @throws UnauthorizedException Token 非法、过期或 Id claim 无法解析为整数时抛出
     */
    public int getUserIdFromToken(String token) {
        try {
            Claims claims = getClaims(token);
            return Integer.parseInt(claims.getId());
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException(AuthError.TOKEN_ERROR);
        }catch (ExpiredJwtException e){
            throw new UnauthorizedException(AuthError.EXPIRED_NEED_REFRESH);
        }catch (MalformedJwtException e){
            throw new UnauthorizedException(AuthError.INVALID_TOKEN);
        }
    }

    /**
     * 从 token 提取账号（存于 Subject claim）。
     * <p>
     * 与 {@link #getUserIdFromToken(String)} 不同：任何解析失败（非法/过期等 JwtException、
     * IllegalArgumentException）<b>返回 {@code null} 而不抛异常</b>，供调用方容错取用。
     * 主要调用方：网关 GlobalJwtFilter，写入下游请求头 {@code X-User-Account}。
     *
     * @param token AccessToken
     * @return 账号；token 无效或过期时返回 {@code null}
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
     * 判断 token 是否已过期（exp claim 与当前时间比较）。
     * <p>
     * 任何解析失败（验签不过、格式非法、已过期抛出等）一律视为已过期返回 {@code true}。
     *
     * @param token AccessToken
     * @return 已过期或 token 无效返回 {@code true}；有效且未过期返回 {@code false}
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
     * 验签解析 token 并返回 Claims 载荷。
     * <p>
     * JJWT 在解析过程中完成签名校验与 exp 过期检查，失败抛<b>原始</b> JJWT 异常
     * （{@code ExpiredJwtException} / {@code MalformedJwtException} / {@code SignatureException}
     * / IllegalArgumentException 等），本方法不做捕获与包装 —— 由调用方决定
     * 是否按协议映射为 {@link UnauthorizedException}（如 {@link #validateToken(String)}）。
     *
     * @param token JWT 字符串
     * @return 解析出的 Claims 载荷
     */
    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 从 token 提取设备会话 ID（自定义 {@code sessionId} claim）。
     * <p>
     * 直接调用 {@link #getClaims(String)} 且<b>不捕获异常</b>：token 无效时会向上抛原始
     * JJWT 异常（而非协议串 {@link UnauthorizedException}）。约定在
     * {@link #validateToken(String)} 通过之后再调用（此时 token 已可信）。主要调用方：
     * 网关 GlobalJwtFilter 与 auth 的 token 校验流程，取之以进行
     * {@code RedisAuthService#validLogoutOrExpire} 会话有效性校验。
     *
     * @param token 已通过校验的 AccessToken
     * @return 设备会话 ID；claim 缺失时返回 {@code null}
     */
    public String getSessionId(String token){
        return getClaims(token).get("sessionId", String.class);
    }

    /**
     * 剥离 Authorization 头的 {@code "Bearer "} 前缀得到裸 token（固定截去前 7 个字符）。
     * <p>
     * 主要调用方：网关 GlobalJwtFilter，在确认请求头以 {@code Bearer } 开头后调用。
     *
     * @param preToken 完整 Authorization 头值，形如 {@code Bearer <token>}
     * @return 剥离前缀后的 token 字符串
     */
    public String extractToken(String preToken){
        return preToken.substring(7);
    }

}
