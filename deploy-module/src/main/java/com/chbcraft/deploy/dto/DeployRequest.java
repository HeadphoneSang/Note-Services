package com.chbcraft.deploy.dto;

/**
 * webhook 请求体
 * <p>
 * 示例：{"action":"deploy"}
 *
 * @param action 动作类型，目前仅支持 deploy
 */
public record DeployRequest(String action) {
}
