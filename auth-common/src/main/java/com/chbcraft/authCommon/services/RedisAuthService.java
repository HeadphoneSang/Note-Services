package com.chbcraft.authCommon.services;

import com.chbcraft.authCommon.exceptions.UnauthorizedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 认证域 Redis 校验工具 — 会话有效性（登出/超时）与账号锁定的统一检查入口。
 * <p>
 * auth 服务（UserService 登录与校验流程）与 gateway（GlobalJwtFilter 鉴权）共用：
 * 两模块各自经自己的 RedisConfig 注册同款 bean，保证校验口径完全一致。
 * <p>
 * 约定：方法「通过则静默返回，失败即抛 {@link IllegalArgumentException}」，
 * 且异常消息为<b>机器可读的协议串</b>（如 {@code account_locked:5}、{@code token_invalidated}），
 * 网关层会将其原样作为 Result 文案下发客户端（前端按前缀解析），
 * 因此调用方与维护者都不得随意改写消息格式。
 */
public class RedisAuthService {

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisScript<Long> expireAndCheckScript;

    public RedisAuthService(StringRedisTemplate stringRedisTemplate,
                         @Qualifier("expireAndCheck") RedisScript<Long> expireAndCheckScript) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.expireAndCheckScript = expireAndCheckScript;
    }

    /**
     * 校验账号是否处于登录锁定状态，未锁定则静默返回，锁定则抛出异常阻断后续流程。
     * <p>
     * 依据锁定 key 的剩余存活时间（TTL，单位秒）分四种情况：
     * <ul>
     *   <li>TTL 为 {@code null} 或 {@code -2}（key 不存在）—— 未锁定，正常返回；</li>
     *   <li>TTL 为 {@code -1}（key 存在但无过期时间）—— 永久锁定，
     *       抛出 {@code account_locked:-1}；</li>
     *   <li>TTL &gt; 0 —— 临时锁定，剩余分钟数向上取整（不足 1 分钟按 1 分钟计），
     *       抛出 {@code account_locked:<minutes>}，如 TTL=119s → {@code account_locked:2}。</li>
     * </ul>
     * <p>
     * 锁定 key 由 auth 登录流程在失败次数达到上限时写入（key 模式见
     * dto-module {@code AuthRedisKey.LOGIN_LOCK}，参数 account），临时锁随 TTL 到期自动解锁。
     *
     * @param lockKey 账号锁定 key，由 {@code AuthRedisKey.LOGIN_LOCK.build(account)} 生成
     * @throws IllegalArgumentException 账号被锁定时抛出，消息格式
     *         {@code account_locked:<minutes|-1>}，网关层原样透传给客户端
     */
    public void validLockTTL(String lockKey){
        Long ttl = stringRedisTemplate.getExpire(
                lockKey, TimeUnit.SECONDS
        );
        if (ttl != null && ttl != -2) {
            if(ttl==-1)
                throw new UnauthorizedException("account_locked:-1");
            long minutes = (ttl + 59) / 60; // 最小返回一分钟
            throw new UnauthorizedException(
                    "account_locked:" + minutes
            );
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
     * @throws IllegalArgumentException 脚本返回 null（执行异常）或 0（已登出/已超时）时抛出，
     *         消息为 {@code token_invalidated}，网关层原样透传给客户端
     */
    public void validLogoutOrExpire(String sessionKey, String sessionId){
        Long result = stringRedisTemplate.execute(expireAndCheckScript, Collections.singletonList(sessionKey),sessionId);
        if (result == null || result == 0) {
            throw new UnauthorizedException("token_invalidated");
        }
    }

}