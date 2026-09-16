package com.sorts.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sorts.ai.config.DeepSeekProperties;
import com.sorts.ai.dto.ChatRequest;
import com.sorts.ai.dto.ChatResponse;
import com.sorts.ai.dto.SuggestedAction;
import com.sorts.ai.enums.ActionType;
import com.sorts.ai.llm.ChatModelClient;
import com.sorts.ai.llm.protocol.ChatMessage;
import com.sorts.ai.llm.protocol.LlmResult;
import com.sorts.ai.llm.protocol.ToolCall;
import com.sorts.ai.support.ConversationStore;
import com.sorts.ai.tool.AiTool;
import com.sorts.ai.tool.ToolLevel;
import com.sorts.ai.tool.ToolRegistry;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对话助手单元测试：工具循环编排、权限拒绝反馈、兜底收尾、上下文裁剪。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceImplTest {

    private static final Long USER_ID = 7L;

    private final ToolJsonCodec jsonCodec = new ToolJsonCodec();

    @Mock
    private ChatModelClient chatModelClient;

    @Mock
    private ConversationStore conversationStore;

    private DeepSeekProperties properties;

    private ToolRegistry registry;

    private ChatServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new DeepSeekProperties();
        properties.setApiKey("test-key");
        properties.setMaxToolRounds(3);
        registry = new ToolRegistry(List.of(
                readTool("queryStatistics", args -> "{\"totalSchedules\":3}"),
                writeTool("createSchedule", args -> "{\"success\":true,\"id\":42}"),
                writeTool("createSchedules", args -> "{\"success\":true,\"createdIds\":[7,8]}")),
                properties, jsonCodec);
        service = new ChatServiceImpl(chatModelClient, properties, registry, conversationStore, jsonCodec);
        when(conversationStore.load(anyLong(), anyString())).thenReturn(List.of());
    }

    @Test
    @DisplayName("模型直接回答：不触发工具，正常返回并落上下文")
    void answersWithoutTools() {
        stubStreams(reply("你好，我是梭灵。"));

        ChatResponse response = service.chat(USER_ID, request("你好", false, false), null);

        assertEquals("你好，我是梭灵。", response.getReply());
        assertNotNull(response.getConversationId());
        assertTrue(response.getSuggestedActions().isEmpty());
        verify(conversationStore).save(eq(USER_ID), anyString(), any());
    }

    @Test
    @DisplayName("一轮工具调用后回答：工具被真正执行，并推导出统计类建议操作")
    void executesToolThenAnswers() {
        stubStreams(
                toolRound(call("call_1", "queryStatistics"), null),
                reply("你这周完成了 3 个日程。"));

        ChatResponse response = service.chat(USER_ID, request("我这周效率怎么样？", false, false), null);

        assertEquals("你这周完成了 3 个日程。", response.getReply());
        List<String> types = response.getSuggestedActions().stream().map(SuggestedAction::getType).toList();
        assertTrue(types.contains(ActionType.VIEW_STATS), types.toString());
        assertTrue(types.contains(ActionType.GENERATE_SUMMARY), types.toString());
    }

    @Test
    @DisplayName("写工具被拒：把「需要开启写入」作为建议操作返回给前端")
    void writeDeniedProducesPermissionAction() {
        stubStreams(
                toolRound(call("call_1", "createSchedule"), null),
                reply("我暂时无法直接创建，需要你先授权。"));

        ChatResponse response = service.chat(USER_ID, request("帮我把明天的学习排上", false, false), null);

        SuggestedAction action = response.getSuggestedActions().stream()
                .filter(item -> ActionType.CREATE_SCHEDULE.equals(item.getType()))
                .findFirst()
                .orElseThrow();
        assertEquals(Boolean.TRUE, action.getPayload().get("requiresWritePermission"));
    }

    @Test
    @DisplayName("双钥匙齐备后创建成功：建议操作带上新建的日程 ID")
    void createdIdsPropagated() {
        properties.getTool().setAllowWrite(true);
        stubStreams(
                toolRound(call("call_1", "createSchedules"), null),
                reply("已经帮你排好两条了。"));

        ChatResponse response = service.chat(USER_ID, request("帮我排两条", true, true), null);

        SuggestedAction action = response.getSuggestedActions().stream()
                .filter(item -> ActionType.CREATE_SCHEDULE.equals(item.getType()))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(7L, 8L), action.getPayload().get("scheduleIds"));
    }

    @Test
    @DisplayName("模型反复调工具直到轮数耗尽：补一次不带工具的收尾调用强制出结论")
    void forcesFinalAnswerAfterRoundLimit() {
        stubStreams(
                toolRound(call("call_1", "queryStatistics"), null),
                toolRound(call("call_2", "queryStatistics"), null),
                toolRound(call("call_3", "queryStatistics"), null),
                reply("结论：你这周保持得不错。"));

        ChatResponse response = service.chat(USER_ID, request("怎么样？", false, false), null);

        assertEquals("结论：你这周保持得不错。", response.getReply());
        // 3 轮工具 + 1 次收尾
        verify(chatModelClient, times(4)).stream(any(), any());
    }

    @Test
    @DisplayName("收尾调用仍无内容：兜底文案而不是空回复")
    void fallbackReplyWhenNothingReturned() {
        stubStreams(
                toolRound(call("call_1", "queryStatistics"), null),
                toolRound(call("call_2", "queryStatistics"), null),
                toolRound(call("call_3", "queryStatistics"), null),
                LlmResult.builder().content(null).toolCalls(List.of()).build());

        ChatResponse response = service.chat(USER_ID, request("怎么样？", false, false), null);

        assertFalse(response.getReply().isEmpty());
        assertTrue(response.getReply().contains("请换个说法"));
    }

    @Test
    @DisplayName("未配置模型密钥：直接返回可读的 503，而不是发起必然失败的调用")
    void missingApiKeyFailsFast() {
        properties.setApiKey("");

        BizException error = assertThrows(BizException.class,
                () -> service.chat(USER_ID, request("你好", false, false), null));

        assertEquals(503, error.getCode());
        assertTrue(error.getMessage().contains("DEEPSEEK_API_KEY"));
        verify(chatModelClient, never()).stream(any(), any());
    }

    @Test
    @DisplayName("上下文只保留「用户问 + 最终答」：工具中间态不入历史")
    void historyKeepsOnlyUserAndFinalAnswer() {
        stubStreams(
                toolRound(call("call_1", "queryStatistics"), null),
                reply("回答。"));

        service.chat(USER_ID, request("问题", false, false), null);

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(conversationStore).save(eq(USER_ID), anyString(), captor.capture());
        List<ChatMessage> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals(ChatMessage.ROLE_USER, saved.get(0).getRole());
        assertEquals(ChatMessage.ROLE_ASSISTANT, saved.get(1).getRole());
    }

    @Test
    @DisplayName("历史超过上限时只保留最近 N 条")
    void trimsHistory() {
        properties.setHistoryLimit(4);
        List<ChatMessage> longHistory = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            longHistory.add(ChatMessage.user("旧问题" + i));
            longHistory.add(ChatMessage.assistant("旧回答" + i));
        }
        when(conversationStore.load(anyLong(), anyString())).thenReturn(longHistory);
        stubStreams(reply("新回答"));

        service.chat(USER_ID, request("新问题", false, false), null);

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(conversationStore).save(eq(USER_ID), anyString(), captor.capture());
        List<ChatMessage> saved = captor.getValue();
        assertEquals(4, saved.size());
        assertEquals("新问题", saved.get(2).getContent());
        assertEquals("新回答", saved.get(3).getContent());
    }

    @Test
    @DisplayName("正文增量回调被透传给模型客户端（SSE 打字机效果来源）")
    void forwardsDeltaCallback() {
        stubStreams(reply("流式回答"));
        Consumer<String> onDelta = chunk -> {
        };

        service.chat(USER_ID, request("你好", false, false), onDelta);

        verify(chatModelClient).stream(any(), eq(onDelta));
    }

    // ==================== 测试辅助 ====================

    private void stubStreams(LlmResult... results) {
        when(chatModelClient.stream(any(), any())).thenReturn(results[0],
                java.util.Arrays.copyOfRange(results, 1, results.length));
    }

    private LlmResult reply(String content) {
        return LlmResult.builder().content(content).toolCalls(List.of()).finishReason("stop").build();
    }

    private LlmResult toolRound(ToolCall call, String content) {
        return LlmResult.builder().content(content).toolCalls(List.of(call)).finishReason("tool_calls").build();
    }

    private ToolCall call(String id, String name) {
        ToolCall.Function function = new ToolCall.Function();
        function.setName(name);
        function.setArguments("{}");
        ToolCall toolCall = new ToolCall();
        toolCall.setId(id);
        toolCall.setType("function");
        toolCall.setFunction(function);
        return toolCall;
    }

    private ChatRequest request(String message, boolean allowWrite, boolean stream) {
        ChatRequest request = new ChatRequest();
        request.setMessage(message);
        request.setAllowWrite(allowWrite);
        request.setStream(stream);
        return request;
    }

    private AiTool readTool(String name, Function<JsonNode, String> behaviour) {
        return tool(name, ToolLevel.READ, behaviour);
    }

    private AiTool writeTool(String name, Function<JsonNode, String> behaviour) {
        return tool(name, ToolLevel.WRITE, behaviour);
    }

    private AiTool tool(String name, ToolLevel level, Function<JsonNode, String> behaviour) {
        return new AiTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return name + " 的说明";
            }

            @Override
            public ToolLevel level() {
                return level;
            }

            @Override
            public Map<String, Object> parameters() {
                return Map.of("type", "object");
            }

            @Override
            public String execute(Long userId, JsonNode args) {
                return behaviour.apply(args);
            }
        };
    }
}
