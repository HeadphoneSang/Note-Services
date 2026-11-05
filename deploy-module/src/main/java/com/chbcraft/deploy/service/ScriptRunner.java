package com.chbcraft.deploy.service;

/**
 * 脚本执行器抽象
 * <p>
 * 生产实现为 ProcessScriptRunner（ProcessBuilder 执行 bash 脚本）；
 * 测试用假实现替换，避免单测依赖真实脚本/操作系统。
 */
public interface ScriptRunner {

    /**
     * 在本地执行脚本
     *
     * @param scriptPath 脚本路径
     * @return 执行结果（成功/退出码/输出）
     */
    ScriptOutcome run(String scriptPath);
}
