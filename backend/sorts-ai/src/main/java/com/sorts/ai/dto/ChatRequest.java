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

    /**
     * 用户上传文件解析出的文本（可选）。
     *
     * <p>只作为<b>本次对话的临时记忆拼接</b>用于提取计划：注入本轮模型上下文，
     * 但<b>不写入会话历史</b>（不持久化、刷新即失效）。文件内容不含可执行计划时，
     * 模型按提示词明确告知用户，不编造计划。</p>
     */
    @Size(max = 20000, message = "文件解析内容过长")
    private String fileContent;

    public boolean streaming() {
        return stream == null || stream;
    }

    public boolean allowWriteEnabled() {
        return allowWrite != null && allowWrite;
    }
}
