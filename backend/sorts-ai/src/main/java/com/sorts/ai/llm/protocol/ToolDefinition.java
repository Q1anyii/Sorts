package com.sorts.ai.llm.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * 提供给模型的工具声明（OpenAI function calling 格式）。
 *
 * <p>由 {@code AiTool#definition()} 生成，注册表按开关过滤后随请求下发——
 * 未声明的工具模型不可能调用，这是能力边界的第一道闸。</p>
 *
 * @author sorts
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolDefinition {

    private String type = "function";

    private Function function;

    public static ToolDefinition of(String name, String description, Map<String, Object> parameters) {
        Function function = new Function();
        function.setName(name);
        function.setDescription(description);
        function.setParameters(parameters);
        ToolDefinition definition = new ToolDefinition();
        definition.setFunction(function);
        return definition;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Function {

        private String name;

        private String description;

        /** JSON Schema 形式的参数说明 */
        private Map<String, Object> parameters;

        /** DeepSeek 支持 strict 模式，要求参数严格符合 schema */
        @JsonProperty("strict")
        private Boolean strict;
    }
}
