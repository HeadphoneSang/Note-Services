package com.chbcraft.deploy.service;

import com.chbcraft.deploy.dto.DeployRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 部署结果通知消息构建器 — 把 DeployRequest + ScriptOutcome + 执行耗时组装成
 * 一条结构化文本，QQ 通知一条消息即可看清本次部署全貌。
 * <p>
 * 纯静态工具类，不依赖 Spring，方便单测。
 */
public final class DeployNotifyMessage {

    /** 脚本输出最多回传的字符数，保留末尾（失败原因通常在结尾） */
    static final int OUTPUT_TAIL_LIMIT = 500;

    /** 提交 sha 展示长度（GitHub 40 位全串太长，取前 7 位惯例） */
    private static final int SHA_SHORT_LEN = 7;

    private static final DateTimeFormatter FINISHED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DeployNotifyMessage() {
    }

    /**
     * 构建结构化部署报告
     *
     * @param request       webhook 请求（taskId / commitAt / commitSha）
     * @param outcome       脚本执行结果（success / exitCode / output）
     * @param elapsedMillis 脚本执行耗时（毫秒）
     * @param finishedAt    部署完成时刻
     */
    public static String build(DeployRequest request, ScriptOutcome outcome,
                               long elapsedMillis, LocalDateTime finishedAt) {
        StringBuilder sb = new StringBuilder();
        sb.append(outcome.isSuccess() ? "🚀 部署成功" : "❌ 部署失败").append('\n');
        sb.append("────────────────\n");
        sb.append("任务: ").append(orDash(request.getTaskId())).append('\n');
        sb.append("提交: ").append(shortSha(request.getCommitSha()))
                .append(" · ").append(orDash(request.getCommitAt())).append('\n');
        sb.append("完成: ").append(finishedAt.format(FINISHED_AT_FORMAT)).append('\n');
        sb.append("耗时: ").append(elapsedMillis / 1000.0).append("s\n");
        sb.append("退出码: ").append(outcome.getExitCode()).append('\n');
        sb.append("────────────────\n");
        sb.append("输出:\n").append(tailOutput(outcome.getOutput()));
        return sb.toString();
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String shortSha(String sha) {
        if (sha == null || sha.isBlank()) {
            return "-";
        }
        return sha.length() <= SHA_SHORT_LEN ? sha : sha.substring(0, SHA_SHORT_LEN);
    }

    private static String tailOutput(String output) {
        if (output == null || output.isBlank()) {
            return "(无输出)";
        }
        if (output.length() <= OUTPUT_TAIL_LIMIT) {
            return output;
        }
        int omitted = output.length() - OUTPUT_TAIL_LIMIT;
        return "…(前 " + omitted + " 字符已省略)\n" + output.substring(omitted);
    }
}
