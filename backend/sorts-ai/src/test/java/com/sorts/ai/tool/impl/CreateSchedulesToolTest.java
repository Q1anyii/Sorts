package com.sorts.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sorts.ai.client.ScheduleClient;
import com.sorts.ai.client.dto.BatchCreateDto;
import com.sorts.ai.client.dto.ScheduleDto;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 批量创建日程单元测试：条数收敛、空数组拒绝、id 汇总。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateSchedulesToolTest {

    private static final Long USER_ID = 7L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ToolJsonCodec jsonCodec = new ToolJsonCodec();

    @Mock
    private ScheduleClient scheduleClient;

    private CreateSchedulesTool tool;

    private ArgumentCaptor<BatchCreateDto> batchCaptor;

    @BeforeEach
    void setUp() {
        tool = new CreateSchedulesTool(jsonCodec, scheduleClient);
        batchCaptor = ArgumentCaptor.forClass(BatchCreateDto.class);
    }

    @Test
    @DisplayName("批量创建：逐条转契约对象，回灌创建数量与 id 列表")
    void createsBatch() throws Exception {
        List<ScheduleDto> created = new ArrayList<>();
        for (long id = 101L; id <= 102L; id++) {
            ScheduleDto dto = new ScheduleDto();
            dto.setId(id);
            dto.setTitle("日程" + id);
            dto.setPlannedStartTime(LocalDateTime.of(2026, 9, 17, 9, 0));
            dto.setPlannedDuration(60);
            created.add(dto);
        }
        when(scheduleClient.batchCreate(eq(USER_ID), batchCaptor.capture())).thenReturn(Result.success(created));

        String payload = """
                {"schedules":[
                  {"title":"A","plannedStartTime":"2026-09-17 09:00:00","plannedDuration":60},
                  {"title":"B","plannedStartTime":"2026-09-17 10:00:00","plannedDuration":90,"priority":"URGENT"}
                ]}""";
        JsonNode data = objectMapper.readTree(tool.execute(USER_ID, objectMapper.readTree(payload)));

        assertEquals(2, batchCaptor.getValue().getSchedules().size());
        assertEquals("B", batchCaptor.getValue().getSchedules().get(1).getTitle());
        assertEquals(Boolean.TRUE, data.get("success").asBoolean());
        assertEquals(2, data.get("createdCount").asInt());
        assertEquals(101L, data.get("createdIds").get(0).asLong());
        assertEquals(102L, data.get("createdIds").get(1).asLong());
    }

    @Test
    @DisplayName("超过 20 条：本地先拒绝，不做无谓的下游调用")
    void rejectsTooManyItems() throws Exception {
        StringBuilder payload = new StringBuilder("{\"schedules\":[");
        for (int i = 0; i < 21; i++) {
            if (i > 0) {
                payload.append(',');
            }
            payload.append("{\"title\":\"t").append(i)
                    .append("\",\"plannedStartTime\":\"2026-09-17 09:00:00\",\"plannedDuration\":30}");
        }
        payload.append("]}");

        JsonNode args = objectMapper.readTree(payload.toString());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(USER_ID, args));
        assertTrue(error.getMessage().contains("20"), error.getMessage());
    }

    @Test
    @DisplayName("空数组：拒绝并提示至少 1 条")
    void rejectsEmptyArray() throws Exception {
        JsonNode args = objectMapper.readTree("{\"schedules\":[]}");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> tool.execute(USER_ID, args));
        assertTrue(error.getMessage().contains("非空数组"), error.getMessage());
    }

    @Test
    @DisplayName("单条参数非法：整批不写入（避免半成功状态）")
    void abortsWholeBatchOnInvalidItem() throws Exception {
        JsonNode args = objectMapper.readTree("""
                {"schedules":[
                  {"title":"A","plannedStartTime":"2026-09-17 09:00:00","plannedDuration":60},
                  {"title":"B","plannedStartTime":"下周","plannedDuration":60}
                ]}""");

        assertThrows(Exception.class, () -> tool.execute(USER_ID, args));
        org.mockito.Mockito.verify(scheduleClient, org.mockito.Mockito.never())
                .batchCreate(eq(USER_ID), any());
    }
}
