package com.sorts.ai.llm;

import com.sorts.ai.llm.protocol.ToolCall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流式工具调用增量累积器。
 *
 * <p>流式下模型把一次工具调用拆成多片下发，大致是：</p>
 * <pre>
 * chunk1: tool_calls[{index:0, id:"call_1", type:"function", function:{name:"querySchedules", arguments:""}}]
 * chunk2: tool_calls[{index:0, function:{arguments:"{\"st"}}]
 * chunk3: tool_calls[{index:0, function:{arguments:"artDate\":\"2026-09-16\"}"}}]
 * </pre>
 * <p>必须按 {@code index} 归并、把 {@code arguments} 的片段按顺序拼接，
 * 否则发给模型的参数会是残缺 JSON。</p>
 *
 * @author sorts
 */
public class ToolCallAccumulator {

    /** LinkedHashMap 保证输出顺序与模型下发顺序一致 */
    private final Map<Integer, ToolCall> byIndex = new LinkedHashMap<>();

    /** 吃进一片增量 */
    public void accept(List<ToolCall> deltas) {
        if (deltas == null || deltas.isEmpty()) {
            return;
        }
        for (ToolCall delta : deltas) {
            int index = delta.getIndex() == null ? byIndex.size() : delta.getIndex();
            ToolCall target = byIndex.computeIfAbsent(index, key -> newIndexedToolCall(index));
            if (delta.getId() != null) {
                target.setId(delta.getId());
            }
            if (delta.getType() != null) {
                target.setType(delta.getType());
            }
            mergeFunction(target, delta.getFunction());
        }
    }

    /** 累积结果 */
    public List<ToolCall> toolCalls() {
        return new ArrayList<>(byIndex.values());
    }

    public boolean isEmpty() {
        return byIndex.isEmpty();
    }

    private ToolCall newIndexedToolCall(int index) {
        ToolCall call = new ToolCall();
        call.setIndex(index);
        return call;
    }

    private void mergeFunction(ToolCall target, ToolCall.Function incoming) {
        if (incoming == null) {
            return;
        }
        if (target.getFunction() == null) {
            target.setFunction(new ToolCall.Function());
        }
        ToolCall.Function current = target.getFunction();
        if (incoming.getName() != null) {
            current.setName(incoming.getName());
        }
        // 参数是分片拼出来的，必须追加而不是覆盖
        if (incoming.getArguments() != null) {
            String previous = current.getArguments();
            current.setArguments(previous == null ? incoming.getArguments() : previous + incoming.getArguments());
        }
    }
}
