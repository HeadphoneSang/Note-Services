package com.chbcraft.authCommon.services;

import com.chbcraft.authCommon.exceptions.AuthError;
import com.chbcraft.authCommon.exceptions.UnauthorizedException;
import com.chcbcraft.dto.enums.AuthRedisKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 认证域 Redis 校验工具 — 会话有效性（登出/超时）与账号锁定的统一检查入口。
 * <p>
 * auth 服务（UserService 登录与校验流程）与 gateway（GlobalJwtFilter 鉴权）共用：
 * 两模块各自经自己的 RedisConfig 注册同款 bean，保证校验口径完全一致。
 * <p>
 * 约定：方法「通过则静默返回，失败即抛 {@link UnauthorizedException}」，
 * 异常携带机器可读错误码 {@link AuthError}（前端按 Result.err 分流）与
 * 用户可读文案（可随意调整，不作为协议），结构化参数经 detail 随 Result.data 下发。
 */
public class RedisAuthService {

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisScript<Long> expireAndCheckScript;

    private final RedisScript<Long> swapRefreshScript;

    public RedisAuthService(StringRedisTemplate stringRedisTemplate,
                         @Qualifier("expireAndCheck") RedisScript<Long> expireAndCheckScript,
                         @Qualifier("swapRefresh") RedisScript<Long> swapRefreshScript) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.expireAndCheckScript = expireAndCheckScript;
        this.swapRefreshScript = swapRefreshScript;
    }

    public RedisAuthService(StringRedisTemplate stringRedisTemplate,
                         @Qualifier("expireAndCheck") RedisScript<Long> expireAndCheckScript) {
        this(stringRedisTemplate, expireAndCheckScript, null);
    }

    /**
     * 校验账号是否处于登录锁定状态，未锁定则静默返回，锁定则抛出异常阻断后续流程。
     * <p>
     * 依据锁定 key 的剩余存活时间（TTL，单位秒）分四种情况：
     * <ul>
     *   <li>TTL 为 {@code null} 或 {@code -2}（key 不存在）—— 未锁定，正常返回；</li>
     *   <li>TTL 为 {@code -1}（key 存在但无过期时间）—— 永久锁定，
     *       抛出 {@code ACCOUNT_LOCKED}，detail 携带 {@code {"minutes": -1}}；</li>
     *   <li>TTL &gt; 0 —— 临时锁定，剩余分钟数向上取整（不足 1 分钟按 1 分钟计），
     *       抛出 {@code ACCOUNT_LOCKED}，detail 携带 {@code {"minutes": <minutes>}}，
     *       如 TTL=119s → minutes=2。</li>
     * </ul>
     * <p>
     * 锁定 key 由 auth 登录流程在失败次数达到上限时写入（key 模式见
     * dto-module {@code AuthRedisKey.LOGIN_LOCK}，参数 account），临时锁随 TTL 到期自动解锁。
     *
     * @param lockKey 账号锁定 key，由 {@code AuthRedisKey.LOGIN_LOCK.build(account)} 生成
     * @throws UnauthorizedException 账号被锁定时抛出，err 为 {@link AuthError#ACCOUNT_LOCKED}，
     *         detail.minutes 为剩余分钟（-1 表示永久锁定），网关层透传给客户端
     */
    public void validLockTTL(String lockKey){
        Long ttl = stringRedisTemplate.getExpire(
                lockKey, TimeUnit.SECONDS
        );
        if (ttl != null && ttl != -2) {
            if(ttl==-1)
                throw new UnauthorizedException(AuthError.ACCOUNT_LOCKED,
                        "账号已被永久锁定，请联系管理员", Map.of("minutes", -1));
            long minutes = (ttl + 59) / 60; // 最小返回一分钟
            throw new UnauthorizedException(AuthError.ACCOUNT_LOCKED,
                    "账号已被锁定，请 " + minutes + " 分钟后再试", Map.of("minutes", minutes));
        }
    }

    /**
     * 校验会话是否仍然有效（未被登出且未超时），有效则静默返回，无效则抛出异常阻断后续流程。
     * <p>
     * 通过 Lua 脚本 {@code expireAndCheck}（见 RedisConfig 与
     * {@code resources/lua/expireAndCheck.lua}）在 Redis 侧<b>原子</b>完成「懒清理 + 存在性检查」：
     * <ol>
     *   <li>取 Redis 服务器当前时间（毫秒），避免应用与 Redis 时钟漂移误判；</li>
     *   <li>{@code ZREMRANGEBYSCORE} 清理会话 ZSET 中过期时间戳 ≤ 当前时间的成员
     *       （member = sessionId，score = 会话过期时间戳，毫秒）；</li>
     *   <li>{@code ZSCORE} 检查当前 sessionId 是否仍存在：存在返回 1（会话有效），
     *       不存在返回 0（已被登出或已超时）。</li>
     * </ol>
     * <p>
     * 用 ZSET 的 score 承载各会话的过期时间、读时惰性清理，一方面无需为每个会话成员
     * 单独设置 TTL，另一方面「清理 + 检查」在同一脚本内执行，规避了两步之间的竞态
     * （例如检查瞬间恰有会话过期）。
     * <p>
     * sessionKey 对应 {@code AuthRedisKey.USER_SESSION_SET}（参数 userId）的 ZSET，
     * 成员由登录（checkAndAdd 脚本）写入。
     *
     * @param sessionKey 用户会话集合 key，由 {@code AuthRedisKey.USER_SESSION_SET.build(userId)} 生成
     * @param sessionId  当前登录设备的会话 ID（取自 JWT claim 中的 sessionId）
     * @throws UnauthorizedException 脚本返回 null（执行异常）或 0（已登出/已超时）时抛出，
     *         err 为 {@link AuthError#TOKEN_INVALIDATED}，网关层透传给客户端
     */
    public void validLogoutOrExpire(String sessionKey, String sessionId){
        Long result = stringRedisTemplate.execute(expireAndCheckScript, Collections.singletonList(sessionKey),sessionId);
        if (result == null || result == 0) {
            throw new UnauthorizedException(AuthError.TOKEN_INVALIDATED);
        }
    }

    /**
     * 登出：删除会话 ZSET 中的指定成员（sessionId）。
     * <p>
     * sessionKey 对应 {@code AuthRedisKey.USER_SESSION_SET}（参数 userId）的 ZSET，
     * 成员由登录（checkAndAdd 脚本）写入。
     *
     * @param sessionKey 用户会话集合 key，由 {@code AuthRedisKey.USER_SESSION_SET.build(userId)} 生成
     * @param sessionId  当前登录设备的会话 ID（取自 JWT claim 中的 sessionId）
     */
    public void logout(String sessionKey, String refreshKey, String sessionId){
        // 懒删除过期session
        long currentTimeMillis = System.currentTimeMillis();
        stringRedisTemplate.opsForZSet().removeRangeByScore(sessionKey, 0, currentTimeMillis);
        // 删除session Id
        stringRedisTemplate.opsForZSet().remove(sessionKey, sessionId);
        // 删除对应的refreshKey
        stringRedisTemplate.delete(refreshKey);
    }

    /**
     * 刷新会话：检查旧 refresh token 是否存在，如果存在则将 curKey 重命名为 oldKey，表示该 refresh token 已使用过。
     * <p>
     * @param userId 用户ID
     * @param sessionId 会话ID
     * @param oldHash 旧refresh token的hash值
     * @param newHash 新refresh token的hash值
     */
    public void compareAndSetNewRefToken(String userId,String sessionId,String oldHash,String newHash){
        String sessionSetKey = AuthRedisKey.USER_SESSION_SET.build(userId);
        String usedRefKey = AuthRedisKey.EXPIRE_REFRESH_TOKEN.build(userId, sessionId);
        String curRefKey = AuthRedisKey.REFRESH_TOKEN.build(userId, sessionId);
        Long result = stringRedisTemplate.execute(swapRefreshScript, List.of(sessionSetKey, usedRefKey, curRefKey), sessionId, oldHash, newHash);
        assert result != null; // script result cannot be null
        if (result == -1) {
            throw new UnauthorizedException(AuthError.REFRESH_TOKEN_INVALID);
        }else if (result == 0) {
            // 销毁当前会话，防止会话劫持，因为前面的CAS替换，所以同时只能有一个对应的session可以进入这个逻辑
            logout(sessionSetKey, curRefKey, sessionId);
            throw new UnauthorizedException(AuthError.REFRESH_TOKEN_REPLAYED);
        }
        // refreshToken替换成功，因为cas，所以只有一个session可以走到这里，保证后续操作线程安全
    }

}