package com.sorts.ai.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 会话保存请求（全量快照式：传什么存什么，缺省字段保持原值）。
 *
 * @author sorts
 */
@Data
public class ConversationSaveRequest {

    @Size(max = 100, message = "会话标题最长 100 字")
    private String title;

    /** 消息列表（服务端按上限截断并清洗 role） */
    private List<ConversationMessage> messages;

    @Size(max = 20_000, message = "草稿最长 20000 字")
    private String draft;

    private Object selectedPlanItems;

    private Object lastGeneratedRange;

    /** 最近一次规划的快照 planId */
    private String planId;

    /** 最近一次规划的建议快照（JSON 数组） */
    private Object planSuggestions;
}
