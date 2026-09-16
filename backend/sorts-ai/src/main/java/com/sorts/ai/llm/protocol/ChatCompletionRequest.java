package com.sorts.ai.llm.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * /chat/completions 请求体。
 *
 * @author sorts
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionRequest {

    private String model;

    private List<ChatMessage> messages;

    /** 可用工具声明；为空时不下发该字段，避免模型误判 */
    private List<ToolDefinition> tools;

    /**
     * 工具选择策略。
     *
     * <p>用 {@code auto}：模型自行判断是否需要调工具；
     * 规划生成场景会显式关闭工具（传 null），强制其只输出结构化内容。</p>
     */
    @JsonProperty("tool_choice")
    private String toolChoice;

    private Double temperature;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private Boolean stream;

    /** 流式时让服务端带上用量统计（DeepSeek 支持） */
    @JsonProperty("stream_options")
    private StreamOptions streamOptions;

    public void enableStreamUsage() {
        this.streamOptions = new StreamOptions();
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StreamOptions {

        @JsonProperty("include_usage")
        private Boolean includeUsage = Boolean.TRUE;
    }
}
