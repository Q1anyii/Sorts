package com.sorts.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sorts.ai.config.DeepSeekProperties;
import com.sorts.ai.dto.ChatRequest;
import com.sorts.ai.dto.ChatResponse;
import com.sorts.ai.dto.SuggestedAction;
import com.sorts.ai.enums.ActionType;
import com.sorts.ai.llm.ChatModelClient;
import com.sorts.ai.llm.protocol.ChatCompletionRequest;
import com.sorts.ai.llm.protocol.ChatMessage;
import com.sorts.ai.llm.protocol.LlmResult;
import com.sorts.ai.llm.protocol.ToolCall;
import com.sorts.ai.llm.protocol.ToolDefinition;
import com.sorts.ai.service.ChatService;
import com.sorts.ai.support.AiPrompts;
import com.sorts.ai.support.ConversationStore;
import com.sorts.ai.tool.ToolRegistry;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 对话助手实现：模型 ↔ 工具的多轮编排。
 *
 * <p>循环的退出条件有两条，缺一不可：模型不再要求调用工具（正常结束），
 * 或达到轮数上限（防打转）。后者必须补一次「不带工具的收尾调用」——
 * 否则模型调完工具就被硬切断，用户看到的是空回复。</p>
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /** 写工具名：用于推导「新建日程」类建议操作，避免匹配字符串散落各处 */
    private static final Set<String> WRITE_TOOLS = Set.of("createSchedule", "createSchedules");

    private final ChatModelClient chatModelClient;

    private final DeepSeekProperties properties;

    private final ToolRegistry toolRegistry;

    private final ConversationStore conversationStore;

    private final ToolJsonCodec jsonCodec;

    @Override
    public ChatResponse chat(Long userId, ChatRequest request, Consumer<String> onDelta) {
        requireConfigured();

        boolean allowWrite = request.allowWriteEnabled();
        String conversationId = StringUtils.hasText(request.getConversationId())
                ? request.getConversationId()
                : UUID.randomUUID().toString();

        List<ChatMessage> history = conversationStore.load(userId, conversationId);
        List<ChatMessage> messages = new ArrayList<>();
        // 写能力事实注入：让模型在对话一开始就明确自己能不能写，
        // 避免「复述确认多轮后才发现写工具未下发」的糟糕体验；同时注入服务器权威日期
        messages.add(ChatMessage.system(AiPrompts.CHAT_SYSTEM
                + (allowWrite ? AiPrompts.writeToolsAvailable() : AiPrompts.writeToolsUnavailable())));
        messages.addAll(history);
        messages.add(ChatMessage.user(request.getMessage()));

        List<ToolDefinition> tools = toolRegistry.definitions(allowWrite);
        Set<String> usedTools = new LinkedHashSet<>();
        Set<Long> createdScheduleIds = new LinkedHashSet<>();
        boolean writeDenied = false;
        String reply = null;

        for (int round = 0; round < properties.getMaxToolRounds(); round++) {
            LlmResult result = chatModelClient.stream(completion(messages, tools), onDelta);
            List<ToolCall> calls = result.getToolCalls();
            if (calls == null || calls.isEmpty()) {
                reply = result.getContent();
                break;
            }
            // 模型这一轮的说明文字 + 工具调用意图，必须原样回传，
            // 否则下一轮它无法把 tool 结果与自己的调用对上
            messages.add(ChatMessage.assistantWithTools(result.getContent(), calls));
            for (ToolCall call : calls) {
                String name = call.getFunction() == null ? "" : call.getFunction().getName();
                usedTools.add(name);
                if (toolRegistry.isWriteDenied(name, allowWrite)) {
                    writeDenied = true;
                }
                String observation = toolRegistry.invoke(userId, call, allowWrite);
                collectCreatedIds(observation, createdScheduleIds);
                messages.add(ChatMessage.tool(call.getId(), observation));
            }
        }

        if (!StringUtils.hasText(reply)) {
            // 轮数耗尽（模型一直在调工具）：关掉工具强制它给结论
            reply = chatModelClient.stream(completion(messages, null), onDelta).getContent();
        }
        if (!StringUtils.hasText(reply)) {
            reply = "抱歉，我这边没能把结果整理出来，请换个说法再问一次。";
        }

        // 上下文只保留「用户问 + 最终答」：中间的 tool 结果是有时效性的快照，
        // 留在上下文里会让模型拿过期数据回答新问题
        List<ChatMessage> nextHistory = new ArrayList<>(history);
        nextHistory.add(ChatMessage.user(request.getMessage()));
        nextHistory.add(ChatMessage.assistant(reply));
        conversationStore.save(userId, conversationId, tail(nextHistory));

        return ChatResponse.builder()
                .conversationId(conversationId)
                .reply(reply)
                .suggestedActions(suggestedActions(usedTools, createdScheduleIds, writeDenied))
                .build();
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "梭灵尚未配置模型密钥：请设置环境变量 DEEPSEEK_API_KEY 后重启 sorts-ai");
        }
    }

    private ChatCompletionRequest completion(List<ChatMessage> messages, List<ToolDefinition> tools) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(properties.getModel());
        request.setMessages(messages);
        if (tools != null && !tools.isEmpty()) {
            request.setTools(tools);
            request.setToolChoice("auto");
        }
        request.setTemperature(properties.getTemperature());
        request.setMaxTokens(properties.getMaxTokens());
        request.setStream(Boolean.TRUE);
        request.enableStreamUsage();
        return request;
    }

    /** 从工具观察结果里挑出新建的日程 ID（失败/无 id 时静默忽略） */
    private void collectCreatedIds(String observation, Set<Long> target) {
        if (!StringUtils.hasText(observation)) {
            return;
        }
        try {
            JsonNode node = jsonCodec.readTree(observation);
            if (node.hasNonNull("id")) {
                target.add(node.get("id").asLong());
            }
            JsonNode ids = node.get("createdIds");
            if (ids != null && ids.isArray()) {
                for (JsonNode id : ids) {
                    target.add(id.asLong());
                }
            }
        } catch (Exception e) {
            log.debug("工具观察结果不是可解析的 JSON，跳过 ID 提取：{}", e.getMessage());
        }
    }

    /**
     * 推导建议操作。
     *
     * <p>只依据「本次实际发生了什么」，不交给模型生成——
     * 模型编出来的按钮点下去会失败，比没有按钮更伤信任。</p>
     */
    private List<SuggestedAction> suggestedActions(Set<String> usedTools, Set<Long> createdScheduleIds,
                                                   boolean writeDenied) {
        Map<String, SuggestedAction> byType = new LinkedHashMap<>();

        if (!createdScheduleIds.isEmpty()) {
            byType.put(ActionType.CREATE_SCHEDULE, SuggestedAction.builder()
                    .type(ActionType.CREATE_SCHEDULE)
                    .label("查看新建的日程")
                    .payload(payload("scheduleIds", new ArrayList<>(createdScheduleIds)))
                    .build());
        } else if (writeDenied) {
            byType.put(ActionType.CREATE_SCHEDULE, SuggestedAction.builder()
                    .type(ActionType.CREATE_SCHEDULE)
                    .label("开启「允许 AI 写入」后重试")
                    .payload(payload("requiresWritePermission", Boolean.TRUE))
                    .build());
        }

        boolean statsTouched = usedTools.stream()
                .anyMatch(name -> "queryStatistics".equals(name) || "querySchedules".equals(name));
        if (statsTouched) {
            byType.put(ActionType.VIEW_STATS, SuggestedAction.builder()
                    .type(ActionType.VIEW_STATS)
                    .label("查看统计详情")
                    .payload(payload("period", "day"))
                    .build());
        }
        if (usedTools.contains("queryStatistics")) {
            byType.put(ActionType.GENERATE_SUMMARY, SuggestedAction.builder()
                    .type(ActionType.GENERATE_SUMMARY)
                    .label("生成今日总结")
                    .payload(payload("type", "DAILY"))
                    .build());
        }
        return new ArrayList<>(byType.values());
    }

    private Map<String, Object> payload(Object... keyValuePairs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            payload.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return payload;
    }

    /** 只保留最近 historyLimit 条，防止上下文无限增长（token 是要花钱的） */
    private List<ChatMessage> tail(List<ChatMessage> messages) {
        int limit = Math.max(properties.getHistoryLimit(), 2);
        if (messages.size() <= limit) {
            return messages;
        }
        return new ArrayList<>(messages.subList(messages.size() - limit, messages.size()));
    }
}
