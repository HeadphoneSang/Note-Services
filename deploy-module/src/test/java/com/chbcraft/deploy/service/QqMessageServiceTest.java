package com.chbcraft.deploy.service;

import com.chbcraft.deploy.client.QqMessageClient;
import com.chbcraft.deploy.dto.QqMessageRequest;
import com.chbcraft.deploy.dto.QqMessageResponse;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QqMessageService 单元测试 — 验证参数透传与失败兜底（通知场景不抛异常）
 */
@ExtendWith(MockitoExtension.class)
class QqMessageServiceTest {

    @Mock
    private QqMessageClient qqMessageClient;

    @Mock
    private FeignException feignException;

    @Test
    void 发送成功应透传userId与消息并返回true() {
        QqMessageResponse ok = new QqMessageResponse();
        ok.setStatus("ok");
        ok.setRetcode(0);
        when(qqMessageClient.sendPrivateMsg(any(QqMessageRequest.class))).thenReturn(ok);

        QqMessageService service = new QqMessageService(qqMessageClient);

        assertTrue(service.sendToUser("1527569640", "hello"));

        ArgumentCaptor<QqMessageRequest> captor = ArgumentCaptor.forClass(QqMessageRequest.class);
        verify(qqMessageClient).sendPrivateMsg(captor.capture());
        assertEquals("1527569640", captor.getValue().getUserId());
        assertEquals("hello", captor.getValue().getMessage());
    }

    @Test
    void 服务端返回失败retcode应返回false() {
        QqMessageResponse failed = new QqMessageResponse();
        failed.setStatus("failed");
        failed.setRetcode(1404);
        when(qqMessageClient.sendPrivateMsg(any(QqMessageRequest.class))).thenReturn(failed);

        QqMessageService service = new QqMessageService(qqMessageClient);

        assertFalse(service.sendToUser("1527569640", "hello"));
    }

    @Test
    void 响应体为null应返回false() {
        when(qqMessageClient.sendPrivateMsg(any(QqMessageRequest.class))).thenReturn(null);

        QqMessageService service = new QqMessageService(qqMessageClient);

        assertFalse(service.sendToUser("1527569640", "hello"));
    }

    @Test
    void 接口抛FeignException应吞掉异常返回false() {
        when(feignException.status()).thenReturn(500);
        when(feignException.contentUTF8()).thenReturn("boom");
        when(qqMessageClient.sendPrivateMsg(any(QqMessageRequest.class))).thenThrow(feignException);

        QqMessageService service = new QqMessageService(qqMessageClient);

        assertFalse(service.sendToUser("1527569640", "hello"));
    }
}
