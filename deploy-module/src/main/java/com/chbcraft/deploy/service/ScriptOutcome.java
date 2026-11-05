package com.chbcraft.deploy.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 脚本执行结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScriptOutcome {

    /** 是否成功（退出码 0 且未被中断/超时） */
    private boolean success;

    /** 脚本退出码；无法取得时为 -1 */
    private int exitCode;

    /** 合并后的标准输出 + 标准错误，用于回传给调用方排查 */
    private String output;

    public static ScriptOutcome ok(int exitCode, String output) {
        return new ScriptOutcome(true, exitCode, output);
    }

    public static ScriptOutcome failure(String output) {
        return new ScriptOutcome(false, -1, output);
    }
}
