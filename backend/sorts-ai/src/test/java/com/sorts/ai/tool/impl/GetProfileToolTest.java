package com.sorts.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sorts.ai.client.UserClient;
import com.sorts.ai.client.dto.UserDto;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

/**
 * 读取用户资料工具单元测试。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetProfileToolTest {

    private static final Long USER_ID = 7L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ToolJsonCodec jsonCodec = new ToolJsonCodec();

    @Mock
    private UserClient userClient;

    private GetProfileTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetProfileTool(jsonCodec, userClient);
    }

    @Test
    @DisplayName("正常返回：昵称、余额、装扮都回灌")
    void returnsProfile() throws Exception {
        UserDto user = new UserDto();
        user.setUsername("qianyi");
        user.setNickname("钱一");
        user.setEmail("me@example.com");
        user.setPoints(1200);
        user.setActiveSkin("night");
        when(userClient.me(USER_ID)).thenReturn(Result.success(user));

        JsonNode data = objectMapper.readTree(tool.execute(USER_ID, objectMapper.createObjectNode()));

        assertEquals("钱一", data.get("nickname").asText());
        assertEquals(1200, data.get("points").asInt());
        assertEquals("night", data.get("activeSkin").asText());
    }

    @Test
    @DisplayName("未设置昵称：回落到用户名，避免模型称呼为空")
    void fallsBackToUsername() throws Exception {
        UserDto user = new UserDto();
        user.setUsername("qianyi");
        when(userClient.me(USER_ID)).thenReturn(Result.success(user));

        JsonNode data = objectMapper.readTree(tool.execute(USER_ID, objectMapper.createObjectNode()));

        assertEquals("qianyi", data.get("nickname").asText());
    }

    @Test
    @DisplayName("不泄露密码等敏感字段（契约本身就没有，此处钉住不回退）")
    void noSensitiveFields() throws Exception {
        UserDto user = new UserDto();
        user.setUsername("qianyi");
        when(userClient.me(USER_ID)).thenReturn(Result.success(user));

        String json = tool.execute(USER_ID, objectMapper.createObjectNode());

        assertFalse(json.contains("password"));
        assertFalse(json.contains("phone"));
    }
}
