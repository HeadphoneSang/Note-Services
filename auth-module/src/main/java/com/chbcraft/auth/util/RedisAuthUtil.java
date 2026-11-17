package com.chbcraft.auth.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisAuthUtil {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisAuthUtil(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
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

}
