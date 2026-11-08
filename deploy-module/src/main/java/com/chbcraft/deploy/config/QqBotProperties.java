package com.chbcraft.deploy.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * QQ 消息机器人配置项（application.yml 前缀 qq-bot）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "qq-bot")
public class QqBotProperties {

    /** 消息接口地址，如 http://47.104.25.40:3000 */
    private String baseUrl;

    /** Bearer 鉴权 token；未配置（空）时请求会被服务端拒绝 */
    private String token;
}
