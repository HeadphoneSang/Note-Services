package com.chbcraft.deploy.service;

import com.chbcraft.deploy.dto.DeployRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DeployNotifyMessage 单元测试 — 验证结构化部署报告的字段、兜底与截断
 */
class DeployNotifyMessageTest {

    private static final LocalDateTime FINISHED = LocalDateTime.of(2026, 11, 8, 21, 1, 30);

    @Test
    void 成功消息应包含部署全貌关键字段() {
        DeployRequest request = new DeployRequest(
                "deploy", "task-42", "2026-11-08T12:59:00Z", "40ce04e1234567890abcdef");

        String msg = DeployNotifyMessage.build(request, ScriptOutcome.ok(0, "pull done\nup done"), 12345, FINISHED);

        assertTrue(msg.startsWith("🚀 部署成功"), msg);
        assertTrue(msg.contains("任务: task-42"), msg);
        assertTrue(msg.contains("提交: 40ce04e · 2026-11-08T12:59:00Z"), msg);
        assertTrue(msg.contains("完成: 2026-11-08 21:01:30"), msg);
        assertTrue(msg.contains("耗时: 12.345s"), msg);
        assertTrue(msg.contains("退出码: 0"), msg);
        assertTrue(msg.contains("输出:\npull done\nup done"), msg);
    }

    @Test
    void 失败消息应以失败头与非零退出码标识() {
        DeployRequest request = new DeployRequest("deploy", "task-42", null, null);

        String msg = DeployNotifyMessage.build(request, new ScriptOutcome(false, 137, "boom"), 500, FINISHED);

        assertTrue(msg.startsWith("❌ 部署失败"), msg);
        assertTrue(msg.contains("退出码: 137"), msg);
        assertTrue(msg.contains("提交: - · -"), msg);
    }

    @Test
    void 空字段应兜底为占位符() {
        DeployRequest request = new DeployRequest("deploy", null, " ", "");

        String msg = DeployNotifyMessage.build(request, ScriptOutcome.ok(0, "  "), 0, FINISHED);

        assertTrue(msg.contains("任务: -"), msg);
        assertTrue(msg.contains("提交: - · -"), msg);
        assertTrue(msg.contains("输出:\n(无输出)"), msg);
    }

    @Test
    void 超长输出只保留末尾并标注省略() {
        String output = "a".repeat(600) + "END";

        String msg = DeployNotifyMessage.build(
                new DeployRequest("deploy", "t", null, null), ScriptOutcome.ok(0, output), 1, FINISHED);

        int omitted = output.length() - DeployNotifyMessage.OUTPUT_TAIL_LIMIT;
        assertTrue(msg.contains("(前 " + omitted + " 字符已省略)"), msg);
        assertTrue(msg.endsWith("END"), "末尾内容必须保留");
        assertFalse(msg.contains(output), "完整输出不应原样出现");
    }
}
