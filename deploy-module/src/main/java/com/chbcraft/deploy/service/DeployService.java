package com.chbcraft.deploy.service;

import com.chbcraft.deploy.config.DeployProperties;
import com.chbcraft.deploy.dto.DeployLog;
import com.chbcraft.deploy.mapper.DeployLogMapper;
import org.springframework.stereotype.Service;

/**
 * 部署服务 — 触发本地部署脚本
 * <p>
 * 脚本具体内容（docker compose pull/up 等）后续需求补充，
 * 当前 scripts/deploy.sh 为占位实现，只打通链路。
 */
@Service
public class DeployService {

    private final DeployProperties properties;
    private final ScriptRunner scriptRunner;

    private final DeployLogMapper deployLogMapper;

    public DeployService(DeployProperties properties, ScriptRunner scriptRunner, DeployLogMapper deployLogMapper) {
        this.properties = properties;
        this.scriptRunner = scriptRunner;
        this.deployLogMapper = deployLogMapper;
    }

    /**
     * 执行配置中指定的部署脚本
     */
    public ScriptOutcome deploy() {
        return scriptRunner.run(properties.getScriptPath());
    }

    public void recordDeployLog(DeployLog deployLog){
        deployLogMapper.insert(deployLog);
    }
}
