package com.sorts.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 会话视图对象。
 *
 * <p>messages / selectedPlanItems / lastGeneratedRange 由后端以 JSON 快照存取，
 * 对外仍是结构化字段：messages 为消息数组，其余两个为透传对象。</p>
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationVO {

    private Long id;

    private String title;

    private List<ConversationMessage> messages;

    /** 输入框草稿 */
    private String draft;

    /** 已勾选的规划项（透传，结构与前端约定一致） */
    private Object selectedPlanItems;

    /** 最近生成区间 {startDate, endDate}（透传） */
    private Object lastGeneratedRange;

    /** 最近一次规划的快照 planId（用于恢复规划面板与采纳） */
    private String planId;

    /** 最近一次规划的建议快照（透传，刷新后恢复勾选面板） */
    private Object planSuggestions;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
