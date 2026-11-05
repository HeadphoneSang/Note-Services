package com.chbcraft.deploy.controller;

import com.chbcraft.deploy.dto.DeployRequest;
import com.chbcraft.deploy.security.BearerTokenValidator;
import com.chbcraft.deploy.service.DeployService;
import com.chbcraft.deploy.service.ScriptOutcome;
import com.chcbcraft.dto.vo.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeployController 单元测试 — 覆盖鉴权失败/参数错误/脚本成功/脚本失败四条路径
 */
@ExtendWith(MockitoExtension.class)
class DeployControllerTest {

    @Mock
    private BearerTokenValidator tokenValidator;

    @Mock
    private DeployService deployService;

    private DeployController controller;

    @BeforeEach
    void setUp() {
        controller = new DeployController(tokenValidator, deployService);
    }

    @Test
    void token无效应返回401且不触发脚本() {
        when(tokenValidator.validate("Bearer bad")).thenReturn(false);

        ResponseEntity<Result<String>> resp =
                controller.deploy("Bearer bad", new DeployRequest("deploy"));

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertEquals(401, resp.getBody().getCode());
        verify(deployService, never()).deploy();
    }

    @Test
    void action非deploy应返回400() {
        when(tokenValidator.validate("Bearer ok")).thenReturn(true);

        ResponseEntity<Result<String>> resp =
                controller.deploy("Bearer ok", new DeployRequest("restart"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals(400, resp.getBody().getCode());
        verify(deployService, never()).deploy();
    }

    @Test
    void action为null应返回400() {
        when(tokenValidator.validate("Bearer ok")).thenReturn(true);

        ResponseEntity<Result<String>> resp =
                controller.deploy("Bearer ok", new DeployRequest(null));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        verify(deployService, never()).deploy();
    }

    @Test
    void 鉴权通过且脚本成功应返回200() {
        when(tokenValidator.validate("Bearer ok")).thenReturn(true);
        when(deployService.deploy()).thenReturn(ScriptOutcome.ok(0, "deploy-ok"));

        ResponseEntity<Result<String>> resp =
                controller.deploy("Bearer ok", new DeployRequest("deploy"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(200, resp.getBody().getCode());
        assertEquals("deploy-ok", resp.getBody().getData());
    }

    @Test
    void 脚本失败应返回500() {
        when(tokenValidator.validate("Bearer ok")).thenReturn(true);
        when(deployService.deploy()).thenReturn(new ScriptOutcome(false, 3, "boom"));

        ResponseEntity<Result<String>> resp =
                controller.deploy("Bearer ok", new DeployRequest("deploy"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertEquals(500, resp.getBody().getCode());
        assertTrue(resp.getBody().getMessage().contains("boom"), "应带脚本输出: " + resp.getBody().getMessage());
    }
}
