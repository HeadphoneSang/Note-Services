package com.chbcraft.deploy.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QQ 消息 DTO 单元测试 — 验证 user_id 下划线序列化与响应解析的容错性
 */
class QqMessageDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 请求体应按接口要求下划线字段名序列化() throws Exception {
        String json = objectMapper.writeValueAsString(new QqMessageRequest("1527569640", "hello"));

        assertTrue(json.contains("\"user_id\":\"1527569640\""), "user_id 字段名必须是下划线风格: " + json);
        assertTrue(json.contains("\"message\":\"hello\""), json);
    }

    @Test
    void 响应解析应容忍未知字段() throws Exception {
        String json = "{\"status\":\"ok\",\"retcode\":0,\"data\":{\"message_id\":123},\"echo\":\"x\"}";

        QqMessageResponse response = objectMapper.readValue(json, QqMessageResponse.class);

        assertEquals("ok", response.getStatus());
        assertEquals(0, response.getRetcode().intValue());
        assertTrue(response.isSuccess());
    }

    @Test
    void retcode非零应判为失败() throws Exception {
        QqMessageResponse response = objectMapper.readValue(
                "{\"status\":\"failed\",\"retcode\":1404}", QqMessageResponse.class);

        assertFalse(response.isSuccess());
    }

    @Test
    void retcode缺失时按status兜底判断() throws Exception {
        QqMessageResponse failed = objectMapper.readValue(
                "{\"status\":\"failed\"}", QqMessageResponse.class);
        QqMessageResponse ok = objectMapper.readValue(
                "{\"status\":\"ok\"}", QqMessageResponse.class);
        QqMessageResponse empty = objectMapper.readValue("{}", QqMessageResponse.class);

        assertFalse(failed.isSuccess());
        assertTrue(ok.isSuccess());
        assertTrue(empty.isSuccess(), "字段全缺失时 HTTP 2xx 已保证送达，不应误判失败");
    }
}
