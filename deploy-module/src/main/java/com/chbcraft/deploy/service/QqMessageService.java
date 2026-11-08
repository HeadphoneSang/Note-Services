package com.chbcraft.deploy.service;

import com.chbcraft.deploy.client.QqMessageClient;
import com.chbcraft.deploy.dto.QqMessageRequest;
import com.chbcraft.deploy.dto.QqMessageResponse;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * QQ 消息服务 — 通过 OpenFeign 远程调用消息接口，给指定用户发私聊消息。
 * <p>
 * 用作通知场景（如部署结果通知）：失败只记日志不抛异常，由返回值告知调用方结果，
 * 避免通知失败影响主流程。
 */
@Slf4j
@Service
public class QqMessageService {

    private final QqMessageClient qqMessageClient;

    public QqMessageService(QqMessageClient qqMessageClient) {
        this.qqMessageClient = qqMessageClient;
    }

    /**
     * 给指定 QQ 用户发送私聊消息
     *
     * @param userId  目标用户 QQ 号
     * @param message 消息内容
     * @return 是否发送成功（网络异常或服务端返回失败均为 false）
     */
    public boolean sendToUser(String userId, String message) {
        try {
            QqMessageResponse response = qqMessageClient.sendPrivateMsg(new QqMessageRequest(userId, message));
            boolean success = response != null && response.isSuccess();
            if (!success) {
                log.warn("QQ 消息发送失败：userId={}，response={}", userId, response);
            }
            return success;
        } catch (FeignException e) {
            log.warn("QQ 消息接口调用异常：userId={}，status={}，body={}", userId, e.status(), e.contentUTF8(), e);
            return false;
        }
    }
}
