package com.chbcraft.deploy.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * deploy-module 配置项（application.yml 前缀 deploy）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "deploy")
public class DeployProperties {

    /** webhook 鉴权 token；为空时拒绝所有请求 */
    private String webhookToken;

    /** 部署脚本路径，触发部署时在本地执行 */
    private String scriptPath;
}
