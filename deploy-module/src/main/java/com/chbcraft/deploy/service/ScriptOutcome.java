package com.chbcraft.deploy.service;

/**
 * 脚本执行结果
 *
 * @param success  是否成功（退出码 0 且未被中断/超时）
 * @param exitCode 脚本退出码；无法取得时为 -1
 * @param output   合并后的标准输出 + 标准错误，用于回传给调用方排查
 */
public record ScriptOutcome(boolean success, int exitCode, String output) {

    public static ScriptOutcome ok(int exitCode, String output) {
        return new ScriptOutcome(true, exitCode, output);
    }

    public static ScriptOutcome failure(String output) {
        return new ScriptOutcome(false, -1, output);
    }
}
