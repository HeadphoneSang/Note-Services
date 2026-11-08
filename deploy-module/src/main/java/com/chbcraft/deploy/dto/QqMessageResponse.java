package com.chbcraft.deploy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * QQ 消息接口响应（OneBot 风格：status/retcode/data），容忍未知字段
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QqMessageResponse {

    /** ok / async / failed */
    private String status;

    /** 0 表示成功 */
    private Integer retcode;

    /** 失败时的错误信息 */
    private String message;

    /** retcode 优先；缺失时以 status 兜底，两者都缺失视为 HTTP 层已成功 */
    public boolean isSuccess() {
        if (retcode != null) {
            return retcode == 0;
        }
        return !"failed".equals(status);
    }
}
