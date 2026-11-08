package com.chbcraft.deploy.client;

import com.chbcraft.deploy.dto.QqMessageRequest;
import com.chbcraft.deploy.dto.QqMessageResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * QQ 消息接口 Feign 客户端。
 * <p>
 * 目标是外部 HTTP 服务（OneBot 风格 /send_private_msg），不走注册中心，
 * 因此用 url 直连 qq-bot.base-url，name 仅作客户端标识。
 */
@FeignClient(
        name = "qq-message",
        url = "${qq-bot.base-url}",
        configuration = QqFeignTokenConfig.class
)
public interface QqMessageClient {

    /** 发送私聊消息给指定用户 */
    @PostMapping(value = "/send_private_msg", consumes = "application/json")
    QqMessageResponse sendPrivateMsg(@RequestBody QqMessageRequest request);
}
