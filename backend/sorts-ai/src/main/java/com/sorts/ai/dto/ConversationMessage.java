package com.sorts.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话中的单条消息。
 *
 * <p>role 取值 user / assistant / system（与需求建议一致）；
 * structuredData 为任意结构化负载（透传，如建议操作、规划结果）。</p>
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessage {

    private String id;

    private String role;

    private String content;

    private String timestamp;

    private Object structuredData;
}
