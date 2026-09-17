package com.sorts.ai.service.impl;

import com.sorts.ai.client.ScheduleClient;
import com.sorts.ai.support.ConversationStore;
import com.sorts.ai.client.dto.BatchCreateDto;
import com.sorts.ai.client.dto.ScheduleDto;
import com.sorts.ai.config.DeepSeekProperties;
import com.sorts.ai.dto.AIPlanRequest;
import com.sorts.ai.dto.AIPlanResponse;
import com.sorts.ai.dto.AdoptPlanRequest;
import com.sorts.ai.dto.PlanAdjustments;
import com.sorts.ai.entity.SchedulePlan;
import com.sorts.ai.enums.PlanStatus;
import com.sorts.ai.llm.ChatModelClient;
import com.sorts.ai.llm.protocol.LlmResult;
import com.sorts.ai.mapper.SchedulePlanMapper;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.common.exception.BizException;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 规划服务单元测试：建议解析、采纳筛选、重复/过期保护、时间调整。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanServiceImplTest {

    private static final Long USER_ID = 7L;

    private static final String PLAN_ID = "plan-uuid-1";

    private static final String SUGGESTIONS_JSON = """
            [{"title":"学习 Java","description":"第3章","suggestedStart":"09:00","duration":120,
              "priority":"HIGH","tags":["学习"],"reason":"上午精力最好"},
             {"title":"跑步","suggestedStart":"16:00","duration":60,"priority":"INVALID","reason":"久坐后活动"}]""";

    private final ToolJsonCodec jsonCodec = new ToolJsonCodec();

    @Mock
    private ChatModelClient chatModelClient;

    @Mock
    private SchedulePlanMapper planMapper;

    @Mock
    private ScheduleClient scheduleClient;

    @Mock
    private ConversationStore conversationStore;

    private DeepSeekProperties properties;

    private PlanServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new DeepSeekProperties();
        properties.setApiKey("test-key");
        service = new PlanServiceImpl(chatModelClient, properties, planMapper, scheduleClient, jsonCodec, conversationStore);
        when(planMapper.insert(any(SchedulePlan.class))).thenAnswer(invocation -> {
            SchedulePlan plan = invocation.getArgument(0);
            plan.setId(1L);
            return 1;
        });
    }

    @Test
    @DisplayName("生成规划：解析建议、落库 DRAFT、返回 planId")
    void generatesPlan() {
        String modelReply = "好的，以下是建议：\n```json\n" + planJson(
                suggestion("学习 Java", "09:00", 120, "HIGH"),
                suggestion("跑步", "16:00", 60, "MEDIUM")) + "\n```";
        when(chatModelClient.complete(any())).thenReturn(LlmResult.builder().content(modelReply).build());

        AIPlanRequest request = new AIPlanRequest();
        request.setUserPrompt("明天上午学 Java 两小时，下午跑步一小时");
        AIPlanResponse response = service.generate(USER_ID, request, false, null);

        assertNotNull(response.getPlanId());
        assertEquals(2, response.getSuggestions().size());
        assertEquals("学习 Java", response.getSuggestions().get(0).getTitle());
        assertEquals(Boolean.FALSE, response.getAdopted());

        ArgumentCaptor<SchedulePlan> captor = ArgumentCaptor.forClass(SchedulePlan.class);
        verify(planMapper).insert(captor.capture());
        SchedulePlan saved = captor.getValue();
        assertEquals(PlanStatus.DRAFT, saved.getStatus());
        assertEquals(LocalDate.now().plusDays(1), saved.getTargetDate());
        assertNotNull(saved.getExpiresAt());
        assertTrue(saved.getSuggestions().contains("学习 Java"));
    }

    @Test
    @DisplayName("模型没吐出 JSON：抛 503 而不是存一条空规划")
    void rejectsUnparsablePlan() {
        when(chatModelClient.complete(any())).thenReturn(LlmResult.builder()
                .content("我想了一下，你的安排很充实，建议保持。").build());

        AIPlanRequest request = new AIPlanRequest();
        request.setUserPrompt("随便安排");
        BizException error = assertThrows(BizException.class, () -> service.generate(USER_ID, request, false, null));

        assertEquals(503, error.getCode());
        verify(planMapper, never()).insert(any(SchedulePlan.class));
    }

    @Test
    @DisplayName("全量采纳：逐条创建并回写 ADOPTED")
    void adoptsAll() {
        stubPlan(PlanStatus.DRAFT, LocalDateTime.now().plusHours(1));
        when(scheduleClient.batchCreate(eq(USER_ID), any())).thenReturn(Result.success(List.of(
                created(101L, "学习 Java"), created(102L, "跑步"))));

        AdoptPlanRequest request = new AdoptPlanRequest();
        request.setPlanId(PLAN_ID);
        List<ScheduleDto> created = service.adopt(USER_ID, request);

        assertEquals(2, created.size());

        ArgumentCaptor<BatchCreateDto> payload = ArgumentCaptor.forClass(BatchCreateDto.class);
        verify(scheduleClient).batchCreate(eq(USER_ID), payload.capture());
        assertEquals(2, payload.getValue().getSchedules().size());
        assertEquals(LocalDateTime.of(LocalDate.now().plusDays(1), java.time.LocalTime.of(9, 0)),
                payload.getValue().getSchedules().get(0).getPlannedStartTime());

        ArgumentCaptor<SchedulePlan> update = ArgumentCaptor.forClass(SchedulePlan.class);
        verify(planMapper).updateById(update.capture());
        assertEquals(PlanStatus.ADOPTED, update.getValue().getStatus());
        assertEquals(2, update.getValue().getAdoptedCount());
        assertEquals("101,102", update.getValue().getCreatedScheduleIds());
    }

    @Test
    @DisplayName("选择性采纳：只创建勾选的建议")
    void adoptsSelectedOnly() {
        stubPlan(PlanStatus.DRAFT, LocalDateTime.now().plusHours(1));
        when(scheduleClient.batchCreate(eq(USER_ID), any()))
                .thenReturn(Result.success(List.of(created(102L, "跑步"))));

        AdoptPlanRequest request = new AdoptPlanRequest();
        request.setPlanId(PLAN_ID);
        request.setSelectedIndices(List.of(1));
        service.adopt(USER_ID, request);

        ArgumentCaptor<BatchCreateDto> payload = ArgumentCaptor.forClass(BatchCreateDto.class);
        verify(scheduleClient).batchCreate(eq(USER_ID), payload.capture());
        assertEquals(1, payload.getValue().getSchedules().size());
        assertEquals("跑步", payload.getValue().getSchedules().get(0).getTitle());
    }

    @Test
    @DisplayName("下标越界：直接拒绝，不做「少建几条」的静默处理")
    void rejectsOutOfRangeIndex() {
        stubPlan(PlanStatus.DRAFT, LocalDateTime.now().plusHours(1));

        AdoptPlanRequest request = new AdoptPlanRequest();
        request.setPlanId(PLAN_ID);
        request.setSelectedIndices(List.of(9));
        BizException error = assertThrows(BizException.class, () -> service.adopt(USER_ID, request));

        assertEquals(400, error.getCode());
        verify(scheduleClient, never()).batchCreate(any(), any());
    }

    @Test
    @DisplayName("重复采纳：拒绝，避免双击产生两份重复日程")
    void rejectsSecondAdopt() {
        stubPlan(PlanStatus.ADOPTED, LocalDateTime.now().plusHours(1));

        AdoptPlanRequest request = new AdoptPlanRequest();
        request.setPlanId(PLAN_ID);
        BizException error = assertThrows(BizException.class, () -> service.adopt(USER_ID, request));

        assertEquals(409, error.getCode());
        verify(scheduleClient, never()).batchCreate(any(), any());
    }

    @Test
    @DisplayName("已过期规划：拒绝并落 EXPIRED 终态")
    void rejectsExpiredPlan() {
        stubPlan(PlanStatus.DRAFT, LocalDateTime.now().minusMinutes(1));

        AdoptPlanRequest request = new AdoptPlanRequest();
        request.setPlanId(PLAN_ID);
        BizException error = assertThrows(BizException.class, () -> service.adopt(USER_ID, request));

        assertEquals(409, error.getCode());
        ArgumentCaptor<SchedulePlan> update = ArgumentCaptor.forClass(SchedulePlan.class);
        verify(planMapper).updateById(update.capture());
        assertEquals(PlanStatus.EXPIRED, update.getValue().getStatus());
        verify(scheduleClient, never()).batchCreate(any(), any());
    }

    @Test
    @DisplayName("手动调整：日期覆盖与整体时间偏移同时生效")
    void appliesAdjustments() {
        stubPlan(PlanStatus.DRAFT, LocalDateTime.now().plusHours(1));
        when(scheduleClient.batchCreate(eq(USER_ID), any()))
                .thenReturn(Result.success(List.of(created(101L, "学习 Java"))));

        AdoptPlanRequest request = new AdoptPlanRequest();
        request.setPlanId(PLAN_ID);
        PlanAdjustments adjustments = new PlanAdjustments();
        adjustments.setDate(LocalDate.of(2026, 10, 1));
        adjustments.setStartOffset(30);
        request.setAdjustments(adjustments);
        service.adopt(USER_ID, request);

        ArgumentCaptor<BatchCreateDto> payload = ArgumentCaptor.forClass(BatchCreateDto.class);
        verify(scheduleClient).batchCreate(eq(USER_ID), payload.capture());
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 30),
                payload.getValue().getSchedules().get(0).getPlannedStartTime());
    }

    @Test
    @DisplayName("优先级非法值归一为 MEDIUM，避免脏数据打到下游")
    void normalizesPriority() {
        stubPlan(PlanStatus.DRAFT, LocalDateTime.now().plusHours(1));
        when(scheduleClient.batchCreate(eq(USER_ID), any()))
                .thenReturn(Result.success(List.of(created(101L, "学习 Java"), created(102L, "跑步"))));

        AdoptPlanRequest request = new AdoptPlanRequest();
        request.setPlanId(PLAN_ID);
        service.adopt(USER_ID, request);

        ArgumentCaptor<BatchCreateDto> payload = ArgumentCaptor.forClass(BatchCreateDto.class);
        verify(scheduleClient).batchCreate(eq(USER_ID), payload.capture());
        List<com.sorts.ai.client.dto.ScheduleSaveDto> schedules = payload.getValue().getSchedules();
        assertEquals("HIGH", schedules.get(0).getPriority());
        assertEquals("MEDIUM", schedules.get(1).getPriority());
    }

    @Test
    @DisplayName("规划不存在：404")
    void planNotFound() {
        when(planMapper.selectOne(any())).thenReturn(null);

        AdoptPlanRequest request = new AdoptPlanRequest();
        request.setPlanId("missing");
        BizException error = assertThrows(BizException.class, () -> service.adopt(USER_ID, request));
        assertEquals(404, error.getCode());
    }

    // ==================== 测试辅助 ====================

    private void stubPlan(String status, LocalDateTime expiresAt) {
        SchedulePlan plan = new SchedulePlan();
        plan.setId(1L);
        plan.setPlanId(PLAN_ID);
        plan.setUserId(USER_ID);
        plan.setTargetDate(LocalDate.now().plusDays(1));
        plan.setStatus(status);
        plan.setSuggestions(SUGGESTIONS_JSON);
        plan.setExpiresAt(expiresAt);
        when(planMapper.selectOne(any())).thenReturn(plan);
    }

    private ScheduleDto created(Long id, String title) {
        ScheduleDto dto = new ScheduleDto();
        dto.setId(id);
        dto.setTitle(title);
        return dto;
    }

    /** 组装成模型会给出的完整 JSON：{"suggestions":[...]} */
    @SafeVarargs
    private final String planJson(java.util.Map<String, Object>... items) {
        return jsonCodec.write(java.util.Map.of("suggestions", List.of(items)));
    }

    /** 单条建议的 JSON 对象 */
    private java.util.Map<String, Object> suggestion(String title, String start, int duration, String priority) {
        java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("title", title);
        item.put("suggestedStart", start);
        item.put("duration", duration);
        item.put("priority", priority);
        item.put("reason", "理由");
        return item;
    }
}
