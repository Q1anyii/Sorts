package com.sorts.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sorts.ai.client.ScheduleClient;
import com.sorts.ai.client.dto.ScheduleDto;
import com.sorts.ai.client.dto.ScheduleQueryDto;
import com.sorts.common.result.PageData;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 查询日程工具单元测试：参数归一、条数上限、精简视图。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuerySchedulesToolTest {

    private static final Long USER_ID = 7L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ToolJsonCodec jsonCodec = new ToolJsonCodec();

    @Mock
    private ScheduleClient scheduleClient;

    private QuerySchedulesTool tool;

    private ArgumentCaptor<ScheduleQueryDto> queryCaptor;

    @BeforeEach
    void setUp() {
        tool = new QuerySchedulesTool(jsonCodec, scheduleClient);
        queryCaptor = ArgumentCaptor.forClass(ScheduleQueryDto.class);
        when(scheduleClient.list(eq(USER_ID), queryCaptor.capture()))
                .thenReturn(Result.success(new PageData<>(List.of(), 0L, 1L, 20L)));
    }

    @Test
    @DisplayName("回灌精简字段：不含 userId 等模型用不上的字段")
    void returnsBriefFieldsOnly() throws Exception {
        ScheduleDto schedule = new ScheduleDto();
        schedule.setId(11L);
        schedule.setUserId(USER_ID);
        schedule.setTitle("学习 Spring Boot");
        schedule.setPlannedStartTime(LocalDateTime.of(2026, 9, 16, 9, 0));
        schedule.setPlannedDuration(120);
        schedule.setActualDuration(3600);
        schedule.setStatus("PENDING");
        schedule.setPriority("HIGH");
        schedule.setTags(List.of("学习"));
        when(scheduleClient.list(eq(USER_ID), any()))
                .thenReturn(Result.success(new PageData<>(List.of(schedule), 1L, 1L, 20L)));

        JsonNode data = objectMapper.readTree(tool.execute(USER_ID, objectMapper.readTree("{\"view\":\"day\"}")));

        assertEquals(1L, data.get("total").asLong());
        assertEquals(1, data.get("count").asInt());
        JsonNode item = data.get("schedules").get(0);
        assertEquals(11L, item.get("id").asLong());
        assertEquals("学习 Spring Boot", item.get("title").asText());
        assertEquals(120, item.get("plannedDuration").asInt());
        assertFalse(item.has("userId"), "精简视图不应包含 userId");
    }

    @Test
    @DisplayName("pageSize 超过 50 被收敛，防止一次拉爆上下文")
    void pageSizeIsCapped() {
        tool.execute(USER_ID, objectMapper.createObjectNode().put("pageSize", 500));
        assertEquals(50, queryCaptor.getValue().getPageSize());
    }

    @Test
    @DisplayName("未提供日期时不解析日期字段，排序与默认视图交由下游处理")
    void dateAbsentStaysNull() {
        tool.execute(USER_ID, objectMapper.createObjectNode());

        ScheduleQueryDto query = queryCaptor.getValue();
        assertNull(query.getDate());
        assertNull(query.getView());
        assertEquals(1, query.getPage());
        assertEquals("plannedStartTime", query.getSort());
    }

    @Test
    @DisplayName("支持斜杠日期写法：模型常输出 2026/09/16")
    void acceptsSlashDate() {
        tool.execute(USER_ID, objectMapper.createObjectNode().put("date", "2026/09/16"));
        assertEquals(java.time.LocalDate.of(2026, 9, 16), queryCaptor.getValue().getDate());
    }

    @Test
    @DisplayName("下游返回业务错误码：转为异常交由注册表回灌，不产出假数据")
    void downstreamErrorIsRejected() {
        when(scheduleClient.list(eq(USER_ID), any()))
                .thenReturn(Result.fail(404, "日程不存在"));

        com.sorts.common.exception.BizException error =
                org.junit.jupiter.api.Assertions.assertThrows(com.sorts.common.exception.BizException.class,
                        () -> tool.execute(USER_ID, objectMapper.createObjectNode()));
        assertEquals("查询日程列表失败：日程不存在", error.getMessage());
        verify(scheduleClient).list(eq(USER_ID), any());
    }
}
