package com.sorts.ai.support;

/**
 * 从模型回复里抠出 JSON 载荷。
 *
 * <p>即便提示词三令五申「只输出 JSON」，模型仍会习惯性地加上
 * {@code ```json} 代码块或一句「好的，以下是建议：」。与其反复强化提示词
 * （会挤占本应用于业务约束的注意力），不如在解析侧做一次稳健的提取。</p>
 *
 * @author sorts
 */
public final class JsonPayloads {

    private JsonPayloads() {
    }

    /**
     * 提取第一个 `{` 到最后一个 `}` 之间的内容。
     *
     * @return 找不到 JSON 边界时返回 null（调用方据此给出可读错误）
     */
    public static String extractObject(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return raw.substring(start, end + 1);
    }
}
