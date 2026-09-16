package com.sorts.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sorts.ai.client.ScheduleClient;
import com.sorts.ai.client.dto.ScheduleDto;
import com.sorts.ai.client.dto.ScheduleSaveDto;
import com.sorts.common.exception.BizException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 创建日程（写工具）单元测试：必填校验、时间容错、参数归一。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateScheduleToolTest {

    private static final Long USER_ID = 7L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ToolJsonCodec jsonCodec = new ToolJsonCodec();

    @Mock
    private ScheduleClient scheduleClient;

    private CreateScheduleTool tool;

    private ArgumentCaptor<ScheduleSaveDto> requestCaptor;

    @BeforeEach
    void setUp() {
        tool = new CreateScheduleTool(jsonCodec, scheduleClient);
        requestCaptor = ArgumentCaptor.forClass(ScheduleSaveDto.class);
        ScheduleDto created = new ScheduleDto();
        created.setId(99L);
        created.setTitle("学习 Java");
        created.setPlannedStartTime(LocalDateTime.of(2026, 9, 17, 9, 0));
        created.setPlannedDuration(120);
        created.setStatus("PENDING");
        created.setPriority("HIGH");
        when(scheduleClient.create(eq(USER_ID), requestCaptor.capture())).thenReturn(Result.success(created));
    }

    @Test
    @DisplayName("正常创建：参数完整落到契约对象，回灌含新建 id")
    void createsSchedule() throws Exception {
        String payload = """
                {"title":"学习 Java","plannedStartTime":"2026-09-17 09:00:00",
                 "plannedDuration":120,"priority":"HIGH","tags":["学习","Java"]}""";

        JsonNode data = objectMapper.readTree(tool.execute(USER_ID, objectMapper.readTree(payload)));

        ScheduleSaveDto sent = requestCaptor.getValue();
        assertEquals("学习 Java", sent.getTitle());
        assertEquals(LocalDateTime.of(2026, 9, 17, 9, 0), sent.getPlannedStartTime());
        assertEquals(120, sent.getPlannedDuration());
        assertEquals(java.util.List.of("学习", "Java"), sent.getTags());

        assertEquals(Boolean.TRUE, data.get("success").asBoolean());
        assertEquals(99L, data.get("id").asLong());
    }

    @Test
    @DisplayName("时间容错：模型给 ISO 写法也能建成功")
    void acceptsIsoTime() throws Exception {
        tool.execute(USER_ID, objectMapper.readTree(
                "{\"title\":\"x\",\"plannedStartTime\":\"2026-09-17T09:00:00\",\"plannedDuration\":30}"));

        assertEquals(LocalDateTime.of(2026, 9, 17, 9, 0), requestCaptor.getValue().getPlannedStartTime());
    }

    @Test
    @DisplayName("缺少标题：直接拒绝，不发起下游写入")
    void missingTitleRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> tool.execute(USER_ID,
                objectMapper.readTree("{\"plannedStartTime\":\"2026-09-17 09:00:00\",\"plannedDuration\":30}")));
        assertTrue(error.getMessage().contains("title"), error.getMessage());
    }

    @Test
    @DisplayName("时长缺失或非正：拒绝并给出单位提示")
    void invalidDurationRejected() throws Exception {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> tool.execute(USER_ID,
                objectMapper.readTree("{\"title\":\"x\",\"plannedStartTime\":\"2026-09-17 09:00:00\",\"plannedDuration\":0}")));
        assertTrue(error.getMessage().contains("分钟"), error.getMessage());
    }

    @Test
    @DisplayName("下游业务错误：抛异常交由注册表回灌，不谎报成功")
    void downstreamErrorPropagates() {
        when(scheduleClient.create(eq(USER_ID), any())).thenReturn(Result.fail(400, "计划时间不能早于当前时间"));

        BizException error = assertThrows(BizException.class, () -> tool.execute(USER_ID,
                objectMapper.readTree("{\"title\":\"x\",\"plannedStartTime\":\"2020-01-01 09:00:00\",\"plannedDuration\":30}")));
        assertEquals("创建日程失败：计划时间不能早于当前时间", error.getMessage());
    }

    @Test
    @DisplayName("可选字段缺省：description/color 保持 null，tags 为空时不下发空数组")
    void optionalFieldsStayNull() throws Exception {
        tool.execute(USER_ID, objectMapper.readTree(
                "{\"title\":\"x\",\"plannedStartTime\":\"2026-09-17 09:00:00\",\"plannedDuration\":30}"));

        ScheduleSaveDto sent = requestCaptor.getValue();
        assertNull(sent.getDescription());
        assertNull(sent.getColor());
        assertNull(sent.getPriority());
        assertNull(sent.getTags());
    }
}
