package com.chbcraft.deploy.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ProcessScriptRunner 单元测试
 * <p>
 * 用 @TempDir 生成临时脚本验证真实执行链路；
 * 若本机无 bash（少见）导致成功路径失败，按失败路径语义排查环境。
 */
class ProcessScriptRunnerTest {

    private final ProcessScriptRunner runner = new ProcessScriptRunner();

    @TempDir
    Path tempDir;

    @Test
    void 脚本不存在应返回失败而非抛异常() {
        ScriptOutcome outcome = runner.run(tempDir.resolve("no-such-script.sh").toString());

        // bash 可用时走退出码 127，bash 不可用时走 IO 异常分支，两者都必须是"失败"结果
        assertFalse(outcome.isSuccess());
    }

    @Test
    void 退出码0的脚本应返回成功() throws IOException {
        Path script = writeScript("echo deploy-ok");

        ScriptOutcome outcome = runner.run(script.toString());

        assertTrue(outcome.isSuccess(), "实际输出: " + outcome.getOutput());
        assertTrue(outcome.getOutput().contains("deploy-ok"));
    }

    @Test
    void 退出码非0的脚本应返回失败并带输出() throws IOException {
        Path script = writeScript("echo oops >&2; exit 3");

        ScriptOutcome outcome = runner.run(script.toString());

        assertFalse(outcome.isSuccess());
        assertEquals(3, outcome.getExitCode());
        assertTrue(outcome.getOutput().contains("oops"), "stderr 应被合并捕获: " + outcome.getOutput());
    }

    /** 写一个 LF 行尾的临时脚本 */
    private Path writeScript(String body) throws IOException {
        Path script = tempDir.resolve("test-" + System.nanoTime() + ".sh");
        Files.writeString(script, "#!/usr/bin/env bash\n" + body + "\n");
        return script;
    }
}
