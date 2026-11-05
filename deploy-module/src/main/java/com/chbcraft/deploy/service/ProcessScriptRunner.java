package com.chbcraft.deploy.service;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ProcessBuilder 脚本执行器 — 在本地用 bash 执行部署脚本
 * <p>
 * 注意：脚本必须可独立运行（非交互），stdout/stderr 会被合并捕获。
 */
@Component
public class ProcessScriptRunner implements ScriptRunner {

    /** 脚本最长执行时间，超时强杀并判失败 */
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    /** 探测 bash 可用性的单条命令超时 */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(5);

    private final String bashCommand;

    public ProcessScriptRunner() {
        this(resolveBashCommand());
    }

    /** 测试可指定解释器；生产走默认构造器（自动解析） */
    ProcessScriptRunner(String bashCommand) {
        this.bashCommand = bashCommand;
    }

    @Override
    public ScriptOutcome run(String scriptPath) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(bashCommand, scriptPath);
            builder.redirectErrorStream(true);
            process = builder.start();

            // 先读完输出再等待退出：合并 stderr 后只有一个管道，读到 EOF 不会与子进程互相阻塞
            String output;
            try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(TIMEOUT.toMinutes(), TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                return ScriptOutcome.failure("脚本执行超时（>" + TIMEOUT.toMinutes() + " 分钟），已强杀: " + output);
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return ScriptOutcome.ok(exitCode, output);
            }
            return new ScriptOutcome(false, exitCode, output);
        } catch (IOException e) {
            return ScriptOutcome.failure("脚本无法执行（路径不存在或 bash 不可用）: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return ScriptOutcome.failure("脚本执行被中断");
        }
    }

    /**
     * 解析可用的 bash 命令。
     * <p>
     * Linux 服务器直接用 bash；Windows 开发机上 System32\bash.exe 是 WSL 垫片，
     * 未装发行版时 execvpe(/bin/bash) 直接失败，因此用 where bash 逐个探测，
     * 取第一个真正能启动的（如 Git Bash），全部失败则按原样返回交给运行时报错。
     */
    static String resolveBashCommand() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return "bash";
        }

        for (String candidate : windowsBashCandidates()) {
            if (probeBash(candidate)) {
                return candidate;
            }
        }
        return "bash";
    }

    /** 枚举 Windows 上 PATH 里所有名为 bash 的可执行文件（where 输出，按 PATH 顺序） */
    private static List<String> windowsBashCandidates() {
        List<String> candidates = new ArrayList<>();
        try {
            Process where = new ProcessBuilder("where", "bash").redirectErrorStream(true).start();
            try (BufferedReader reader = where.inputReader()) {
                reader.lines().map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .forEach(candidates::add);
            }
            if (!where.waitFor(PROBE_TIMEOUT.getSeconds(), TimeUnit.SECONDS)) {
                where.destroyForcibly();
            }
        } catch (IOException e) {
            // where 不可用（几乎不可能），退回直接用 bash
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return candidates;
    }

    /** 用 bash -c "exit 0" 探测候选是否真正可执行（WSL 垫片会在此失败） */
    private static boolean probeBash(String command) {
        try {
            Process probe = new ProcessBuilder(command, "-c", "exit 0").redirectErrorStream(true).start();
            try (BufferedReader reader = probe.inputReader()) {
                reader.lines().forEach(line -> { });
            }
            return probe.waitFor(PROBE_TIMEOUT.getSeconds(), TimeUnit.SECONDS) && probe.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
