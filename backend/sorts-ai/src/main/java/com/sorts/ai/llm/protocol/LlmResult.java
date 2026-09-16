package com.sorts.ai.llm.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 模型单次调用的归一化结果。
 *
 * <p>流式与非流式都归一到这个结构，上层（对话/规划/总结）无需关心传输方式差异。</p>
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResult {

    /** 文本内容，可能为空（例如纯工具调用轮） */
    private String content;

    /** 模型要求执行的工具调用，空列表表示已给出最终答复 */
    private List<ToolCall> toolCalls;

    /** stop / tool_calls / length */
    private String finishReason;

    /** token 用量，未开启统计时为 null */
    private Map<String, Integer> usage;

    /** 本次调用是否消耗了输出额度（用于判断 length 截断告警） */
    public boolean truncated() {
        return "length".equals(finishReason);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
