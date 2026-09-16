package com.sorts.ai.llm;

import com.sorts.ai.llm.protocol.ChatCompletionRequest;
import com.sorts.ai.llm.protocol.LlmResult;

import java.util.function.Consumer;

/**
 * 对话模型客户端（梭灵的大脑出口）。
 *
 * <p>这层抽象是刻意留的：当前实现是自写的 OpenAI 兼容客户端；
 * 将来若升级到 Spring Boot 3.4+ 并引入 Spring AI，只需新增一个实现类
 * （把 {@code ChatClient} 包进来），业务代码零改动。</p>
 *
 * <p>两个方法都接收完整请求体，而不是「messages + tools」两个参数：
 * 这样温度、最大输出、是否流式等都由上层决定，实现只管发送与解析。</p>
 *
 * @author sorts
 */
public interface ChatModelClient {

    /**
     * 一次性拿完整回复（含可能存在的工具调用）。
     *
     * @return 归一化结果；无工具调用时 {@code content} 即最终答复
     */
    LlmResult complete(ChatCompletionRequest request);

    /**
     * 流式调用：文本增量逐段回调，返回值里包含累积后的完整文本与工具调用。
     *
     * @param onContentDelta 每段文本增量回调（用于推 SSE），可为 null
     */
    LlmResult stream(ChatCompletionRequest request, Consumer<String> onContentDelta);
}
