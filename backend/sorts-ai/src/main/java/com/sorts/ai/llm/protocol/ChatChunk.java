package com.sorts.ai.llm.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * /chat/completions 流式响应中的单个 chunk（对应一条 {@code data:} 事件）。
 *
 * <p>与 {@link ChatCompletionResponse} 的区别：这里是 {@code delta} 增量，
 * 内容与工具调用都会被拆成多片，需要累积。</p>
 *
 * @author sorts
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatChunk {

    private String id;

    private String model;

    private List<Choice> choices;

    /** 仅在开启 stream_options.include_usage 时，最后一个 chunk 携带 */
    private ChatCompletionResponse.Usage usage;

    public Choice firstChoice() {
        return choices == null || choices.isEmpty() ? null : choices.get(0);
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {

        private Integer index;

        private Delta delta;

        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Delta {

        private String role;

        private String content;

        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;
    }
}
