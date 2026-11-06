package com.chbcraft.deploy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * webhook 请求体
 * <p>
 * 示例：{"action":"deploy"}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeployRequest {

    /** 动作类型，目前仅支持 deploy */
    private String action;

    private String taskId;

    private String currentAt;
}
