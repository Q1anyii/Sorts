package com.sorts.ai.service;

import com.sorts.ai.dto.ChatRequest;
import com.sorts.ai.dto.ChatResponse;

import java.util.function.Consumer;

/**
 * AI 对话助手。
 *
 * @author sorts
 */
public interface ChatService {

    /**
     * 与梭灵对话。
     *
     * @param onDelta 正文增量回调（用于 SSE 推送）；不流式时传 null
     * @return 最终回复与建议操作
     */
    ChatResponse chat(Long userId, ChatRequest request, Consumer<String> onDelta);
}
