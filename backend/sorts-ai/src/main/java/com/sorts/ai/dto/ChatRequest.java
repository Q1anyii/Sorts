package com.sorts.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 对话请求（对应 api-spec.json 的 POST /ai/chat 请求体）。
 *
 * @author sorts
 */
@Data
public class ChatRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息长度不能超过 2000 字")
    private String message;

    /** 会话ID：多轮对话时由前端回传；不传则新开会话 */
    private String conversationId;

    /** 是否流式返回，默认 true（打字机效果） */
    private Boolean stream = Boolean.TRUE;

    /**
     * 是否允许本次对话写入数据（双钥匙的第二把）。
     *
     * <p>api-spec 未定义该字段，属于本项目的<b>扩展字段</b>：
     * 前端在用户点击「允许 AI 帮我创建日程」后置为 true。
     * 只有它 与服务端 {@code sorts.ai.tool.allow-write} 同时为真，写工具才可用。</p>
     */
    private Boolean allowWrite = Boolean.FALSE;

    public boolean streaming() {
        return stream == null || stream;
    }

    public boolean allowWriteEnabled() {
        return allowWrite != null && allowWrite;
    }
}
