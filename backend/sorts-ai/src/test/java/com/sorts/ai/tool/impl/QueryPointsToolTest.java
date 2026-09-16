package com.sorts.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sorts.ai.client.UserClient;
import com.sorts.ai.client.dto.PointsDto;
import com.sorts.ai.client.dto.PointsLogDto;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 查询光阴砂工具单元测试：条数收敛、流水回灌。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueryPointsToolTest {

    private static final Long USER_ID = 7L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ToolJsonCodec jsonCodec = new ToolJsonCodec();

    @Mock
    private UserClient userClient;

    private QueryPointsTool tool;

    private ArgumentCaptor<Integer> limitCaptor;

    @BeforeEach
    void setUp() {
        tool = new QueryPointsTool(jsonCodec, userClient);
        limitCaptor = ArgumentCaptor.forClass(Integer.class);
    }

    @Test
    @DisplayName("返回余额与流水，只回灌模型需要的字段")
    void returnsBalanceAndLogs() throws Exception {
        PointsLogDto log = new PointsLogDto();
        log.setId(1L);
        log.setChangeAmount(50);
        log.setBalance(1250);
        log.setReason("完成日程");
        log.setCreatedAt(LocalDateTime.of(2026, 9, 16, 10, 0));
        PointsDto points = new PointsDto();
        points.setPoints(1250);
        points.setLogs(List.of(log));
        when(userClient.points(eq(USER_ID), anyInt())).thenReturn(Result.success(points));

        JsonNode data = objectMapper.readTree(tool.execute(USER_ID, objectMapper.createObjectNode()));

        assertEquals(1250, data.get("balance").asInt());
        JsonNode item = data.get("logs").get(0);
        assertEquals(50, item.get("changeAmount").asInt());
        assertEquals("完成日程", item.get("reason").asText());
        // id 对模型无意义，不回灌
        assertFalse(item.has("id"));
    }

    @Test
    @DisplayName("limit 上限 50：模型给 999 也只取 50，避免超量拉取")
    void limitCappedAtFifty() {
        when(userClient.points(eq(USER_ID), limitCaptor.capture()))
                .thenReturn(Result.success(new PointsDto()));

        tool.execute(USER_ID, objectMapper.createObjectNode().put("limit", 999));

        assertEquals(50, limitCaptor.getValue());
    }

    @Test
    @DisplayName("limit 下限 1：负数被抬到 1，避免下游收到非法参数")
    void limitFlooredAtOne() {
        when(userClient.points(eq(USER_ID), limitCaptor.capture()))
                .thenReturn(Result.success(new PointsDto()));

        tool.execute(USER_ID, objectMapper.createObjectNode().put("limit", -5));

        assertEquals(1, limitCaptor.getValue());
    }
}
