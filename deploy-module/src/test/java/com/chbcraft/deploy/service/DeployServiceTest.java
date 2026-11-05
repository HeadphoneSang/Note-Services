package com.chbcraft.deploy.service;

import com.chbcraft.deploy.config.DeployProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DeployService 单元测试 — 验证按配置路径把脚本执行委托给 ScriptRunner
 */
class DeployServiceTest {

    /** 记录调用参数的假执行器 */
    private static class RecordingRunner implements ScriptRunner {
        String invokedPath;
        int invokeCount;

        @Override
        public ScriptOutcome run(String scriptPath) {
            invokedPath = scriptPath;
            invokeCount++;
            return ScriptOutcome.ok(0, "done");
        }
    }

    @Test
    void deploy应把配置中的脚本路径传给执行器() {
        RecordingRunner runner = new RecordingRunner();
        DeployService service = new DeployService(
                new DeployProperties("token", "scripts/deploy.sh"), runner);

        ScriptOutcome outcome = service.deploy();

        assertTrue(outcome.success());
        assertEquals(1, runner.invokeCount);
        assertEquals("scripts/deploy.sh", runner.invokedPath);
    }

    @Test
    void deploy应原样返回执行器结果() {
        ScriptOutcome failure = ScriptOutcome.failure("boom");
        DeployService service = new DeployService(
                new DeployProperties("token", "any.sh"), path -> failure);

        assertSame(failure, service.deploy());
    }
}
