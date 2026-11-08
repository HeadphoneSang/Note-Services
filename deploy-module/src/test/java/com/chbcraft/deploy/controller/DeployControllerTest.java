package com.chbcraft.deploy.controller;

import com.chbcraft.deploy.dto.DeployRequest;
import com.chbcraft.deploy.security.BearerTokenValidator;
import com.chbcraft.deploy.service.DeployService;
import com.chbcraft.deploy.service.QqMessageService;
import com.chbcraft.deploy.service.ScriptOutcome;
import com.chcbcraft.dto.vo.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * DeployController 单元测试 — 验证部署回调的结构化 QQ 通知与 HTTP 状态码语义
 */
@ExtendWith(MockitoExtension.class)
class DeployControllerTest {

    @Mock
    private BearerTokenValidator tokenValidator;

    @Mock
    private DeployService deployService;

    @Mock
    private QqMessageService qqMessageService;

    private DeployController controller() {
        return new DeployController(tokenValidator, deployService, qqMessageService);
    }

    private static DeployRequest deployRequest() {
        return new DeployRequest("deploy", "task-7", "2026-11-08T13:00:00Z", "abcdef1234abcdef");
    }

    @Test
    void 部署成功应发送结构化消息并返回200() {
        when(tokenValidator.validate(anyString())).thenReturn(true);
        when(deployService.deploy()).thenReturn(ScriptOutcome.ok(0, "up done"));

        ResponseEntity<Result<String>> resp = controller().deploy("Bearer t", deployRequest());

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(qqMessageService).sendToUser(captor.capture());
        String sent = captor.getValue();
        assertTrue(sent.startsWith("🚀 部署成功"), sent);
        assertTrue(sent.contains("任务: task-7"), sent);
        assertTrue(sent.contains("提交: abcdef1 · 2026-11-08T13:00:00Z"), sent);
        assertTrue(sent.contains("退出码: 0"), sent);
        verify(deployService).recordDeployLog(any());
    }

    @Test
    void 部署失败应发送失败结构化消息并返回500() {
        when(tokenValidator.validate(anyString())).thenReturn(true);
        when(deployService.deploy()).thenReturn(ScriptOutcome.failure("boom"));

        ResponseEntity<Result<String>> resp = controller().deploy("Bearer t", deployRequest());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(qqMessageService).sendToUser(captor.capture());
        assertTrue(captor.getValue().startsWith("❌ 部署失败"), captor.getValue());
        verify(deployService).recordDeployLog(any());
    }

    @Test
    void 鉴权失败应返回401且不发通知() {
        when(tokenValidator.validate(anyString())).thenReturn(false);

        ResponseEntity<Result<String>> resp = controller().deploy("bad", deployRequest());

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        verifyNoInteractions(qqMessageService, deployService);
    }

    @Test
    void 非deploy动作应返回400且不执行脚本() {
        when(tokenValidator.validate(anyString())).thenReturn(true);

        ResponseEntity<Result<String>> resp = controller().deploy(
                "Bearer t", new DeployRequest("rollback", "task-7", null, null));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        verifyNoInteractions(qqMessageService);
        verify(deployService, never()).deploy();
    }
}
