package com.chbcraft.deploy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * deploy-module 配置项（application.yml 前缀 deploy）
 *
 * @param webhookToken webhook 鉴权 token；为空时拒绝所有请求
 * @param scriptPath   部署脚本路径，触发部署时在本地执行
 */
@ConfigurationProperties(prefix = "deploy")
public record DeployProperties(String webhookToken, String scriptPath) {
}
