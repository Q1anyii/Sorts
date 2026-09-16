package com.sorts.ai.llm.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * OpenAI 兼容协议的一条对话消息。
 *
 * <p>四种角色：{@code system} 系统提示、{@code user} 用户输入、
 * {@code assistant} 模型回复（可能带 tool_calls）、{@code tool} 工具执行结果回灌。</p>
 *
 * @author sorts
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {

    public static final String ROLE_SYSTEM = "system";

    public static final String ROLE_USER = "user";

    public static final String ROLE_ASSISTANT = "assistant";

    public static final String ROLE_TOOL = "tool";

    private String role;

    private String content;

    /** role=assistant 时，模型发起的工具调用 */
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    /** role=tool 时，对应的工具调用 ID */
    @JsonProperty("tool_call_id")
    private String toolCallId;

    public static ChatMessage system(String content) {
        return of(ROLE_SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return of(ROLE_USER, content);
    }

    public static ChatMessage assistant(String content) {
        return of(ROLE_ASSISTANT, content);
    }

    /** 带回工具调用的 assistant 消息（原样回传，模型据此对齐后续 tool 结果） */
    public static ChatMessage assistantWithTools(String content, List<ToolCall> toolCalls) {
        ChatMessage message = of(ROLE_ASSISTANT, content);
        message.setToolCalls(toolCalls);
        return message;
    }

    /** 工具执行结果 */
    public static ChatMessage tool(String toolCallId, String content) {
        ChatMessage message = of(ROLE_TOOL, content);
        message.setToolCallId(toolCallId);
        return message;
    }

    private static ChatMessage of(String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}
