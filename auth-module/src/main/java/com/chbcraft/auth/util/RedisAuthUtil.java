package com.chbcraft.auth.util;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
public class RedisAuthUtil {

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisScript<Long> expireAndCheckScript;

    public RedisAuthUtil(StringRedisTemplate stringRedisTemplate,
                         @Qualifier("expireAndCheck") RedisScript<Long> expireAndCheckScript) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.expireAndCheckScript = expireAndCheckScript;
    }

    public void validLockTTL(String lockKey){
        Long ttl = stringRedisTemplate.getExpire(
                lockKey, TimeUnit.SECONDS
        );
        if (ttl != null && ttl != -2) {
            if(ttl==-1)
                throw new IllegalArgumentException("account_locked:-1");
            long minutes = (ttl + 59) / 60; // 最小返回一分钟
            throw new IllegalArgumentException(
                    "account_locked:" + minutes
            );
        }
    }

    public void validLogoutOrExpire(String sessionKey, String sessionId){
        Long result = stringRedisTemplate.execute(expireAndCheckScript, Collections.singletonList(sessionKey),sessionId);
        if (result == null || result == 0) {
            throw new IllegalArgumentException("token_invalidated");
        }
    }

}
