package com.chbcraft.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
public class RedisConfig {


    @Bean(name = "checkAndAdd")
    public RedisScript<Long> checkAndAddScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/checkAndAdd.lua")));
        script.setResultType(Long.class);   // Lua 返回 1/0
        return script;
    }
}
