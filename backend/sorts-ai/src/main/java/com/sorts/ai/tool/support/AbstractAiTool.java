package com.sorts.ai.tool.support;

import com.sorts.ai.tool.AiTool;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具实现的公共基类：统一「结果序列化为 JSON」这一动作。
 *
 * <p>回灌给模型的观察结果刻意用 JSON 而不是自然语言：结构化文本里模型引用数字、ID
 * 时不易失真（例如「12 分钟」被它复述成「20 分钟」）。</p>
 *
 * @author sorts
 */
@RequiredArgsConstructor
public abstract class AbstractAiTool implements AiTool {

    /** 专用编解码器：保证时间字段稳定输出 ISO-8601，不受全局 Jackson 配置影响 */
    protected final ToolJsonCodec jsonCodec;

    /** 序列化观察结果 */
    protected String json(Object value) {
        return jsonCodec.write(value);
    }

    /** 有序的结果容器，保证回灌给模型的字段顺序稳定 */
    protected static Map<String, Object> result() {
        return new LinkedHashMap<>();
    }
}
