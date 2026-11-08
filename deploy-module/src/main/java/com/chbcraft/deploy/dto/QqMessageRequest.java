package com.chbcraft.deploy.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * QQ 私聊消息请求体
 * <p>
 * 示例：{"user_id":"1527569640","message":"hello"}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QqMessageRequest {

    /** 目标用户 QQ 号 */
    @JsonProperty("user_id")
    private String userId;

    /** 消息内容 */
    private String message;
}
