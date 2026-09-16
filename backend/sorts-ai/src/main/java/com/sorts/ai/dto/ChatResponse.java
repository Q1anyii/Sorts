package com.sorts.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 对话响应（对应 api-spec.json 的 POST /ai/chat 响应 data）。
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String conversationId;

    /** AI 回复正文（Markdown） */
    private String reply;

    /** 建议操作：前端渲染为快捷按钮 */
    private List<SuggestedAction> suggestedActions;
}
