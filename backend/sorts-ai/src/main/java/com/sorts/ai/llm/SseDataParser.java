package com.sorts.ai.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sorts.ai.llm.protocol.ChatChunk;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * SSE 行解析器：把 {@code data: {...}} 文本行转成 {@link ChatChunk}。
 *
 * <p>刻意做成无状态的纯解析器（只依赖 ObjectMapper），这样流式解析逻辑
 * 可以脱离网络单独做单元测试——流式最容易出错的地方恰恰是这里。</p>
 *
 * <p>按 SSE 规范：以 {@code :} 开头的是注释、空行是事件分隔符、
 * {@code event:}/{@code id:} 等字段本场景用不到一律忽略；
 * 数据载荷为 {@code [DONE]} 表示流结束。</p>
 *
 * @author sorts
 */
@Component
@RequiredArgsConstructor
public class SseDataParser {

    private static final String DATA_PREFIX = "data:";

    private static final String DONE = "[DONE]";

    private final ObjectMapper objectMapper;

    /**
     * 解析单行。
     *
     * @return 不含可用数据块时返回 {@link Optional#empty()}
     */
    public Optional<ChatChunk> parseLine(String line) {
        if (line == null) {
            return Optional.empty();
        }
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.startsWith(":")) {
            return Optional.empty();
        }
        if (!trimmed.startsWith(DATA_PREFIX)) {
            return Optional.empty();
        }
        String payload = trimmed.substring(DATA_PREFIX.length()).strip();
        if (payload.isEmpty() || DONE.equals(payload)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, ChatChunk.class));
        } catch (JsonProcessingException e) {
            // 解析失败不静默丢弃：流式场景下静默会表现为「AI 回复缺一截」，极难排查
            String preview = payload.length() > 200 ? payload.substring(0, 200) + "..." : payload;
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "梭灵的流式响应解析失败：" + preview);
        }
    }

    /** 是否为流结束标记 */
    public boolean isDone(String line) {
        return line != null && DONE.equals(line.strip().replaceFirst("^data:", "").strip());
    }
}
