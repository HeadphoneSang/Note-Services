package com.chbcraft.deploy.controller;

import com.chbcraft.deploy.dto.DeployLog;
import com.chbcraft.deploy.dto.DeployRequest;
import com.chbcraft.deploy.security.BearerTokenValidator;
import com.chbcraft.deploy.service.DeployService;
import com.chbcraft.deploy.service.QqMessageService;
import com.chbcraft.deploy.service.ScriptOutcome;
import com.chcbcraft.dto.vo.Result;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 部署 webhook 控制器
 * <p>
 * 供 CI（GitHub Actions）在镜像推送完成后回调，触发服务器本地部署脚本：
 * <pre>
 * curl --fail-with-body \
 *   --request POST \
 *   --header "Authorization: Bearer ${DEPLOY_WEBHOOK_TOKEN}" \
 *   --header "Content-Type: application/json" \
 *   --data '{"action":"deploy"}' \
 *   http://&lt;server&gt;:8083/webhook/deploy
 * </pre>
 * <p>
 * 与业务接口不同：这里用真实 HTTP 状态码（401/400/500），
 * 保证 curl --fail-with-body 能让 CI 步骤正确失败。
 */
@RestController
public class DeployController {

    private final BearerTokenValidator tokenValidator;
    private final DeployService deployService;
    private final QqMessageService qqMessageService;

    public DeployController(BearerTokenValidator tokenValidator, DeployService deployService, QqMessageService qqMessageService) {
        this.tokenValidator = tokenValidator;
        this.deployService = deployService;
        this.qqMessageService = qqMessageService;
    }

    /**
     * POST /webhook/deploy — 触发部署脚本
     */
    @PostMapping("/webhook/deploy")
    public ResponseEntity<Result<String>> deploy(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody DeployRequest request) {

        // 1. Bearer token 鉴权
        if (!tokenValidator.validate(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.unauthorized("无效的 webhook token"));
        }

        // 2. 请求体校验
        if (!"deploy".equals(request.getAction())) {
            return ResponseEntity.badRequest()
                    .body(Result.error(400, "不支持的 action: " + request.getAction()));
        }

        // 3. 触发部署脚本
        ScriptOutcome outcome = deployService.deploy();
        if (!outcome.isSuccess()) {
            String msg = "部署脚本执行失败(exit=" + outcome.getExitCode() + "): " + outcome.getOutput();
            deployService.recordDeployLog(new DeployLog(null, request.getTaskId(), msg.substring(0,Math.min(msg.length(),64)), request.getCommitAt(),request.getCommitSha(),"deploy", "failure"));
            qqMessageService.sendToUser("部署失败：" + msg);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error(500, msg));
        }
        deployService.recordDeployLog(new DeployLog(null, request.getTaskId(), outcome.getOutput().substring(0,Math.min(outcome.getOutput().length(), 64)), request.getCommitAt(), request.getCommitSha(),"deploy", "success"));
        qqMessageService.sendToUser("部署成功：" + outcome.getOutput());
        return ResponseEntity.ok(Result.success("部署脚本已执行", outcome.getOutput()));
    }
}
