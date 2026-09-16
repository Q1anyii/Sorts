package com.sorts.ai.llm.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 模型发起的一次工具调用。
 *
 * <p>流式响应里，同一个调用会被拆成多个增量片段下发，用 {@code index} 归并；
 * 非流式响应中 {@code index} 为 null。</p>
 *
 * @author sorts
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolCall {

    /** 流式增量里的归并下标（非流式为 null） */
    private Integer index;

    private String id;

    /** 固定为 function */
    private String type;

    private Function function;

    /** 工具调用内容为空判断（增量的首片只有 id/name，参数后续才到） */
    public boolean isEmpty() {
        return function == null || function.getName() == null;
    }

    /** 函数名与参数的 JSON 字符串 */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Function {

        private String name;

        /** 模型给出的参数，JSON 字符串（可能为空串，表示无参数） */
        @JsonProperty("arguments")
        private String arguments;
    }
}
