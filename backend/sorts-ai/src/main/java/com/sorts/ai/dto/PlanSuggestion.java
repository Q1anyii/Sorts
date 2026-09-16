package com.sorts.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单条规划建议（对应 api-spec.json 的 AIPlanResponse.suggestions 项）。
 *
 * <p>{@code suggestedStart} 只有时分（HH:mm），日期由规划的目标日期决定——
 * 这样模型不必处理「今天是几号」，也就不会算错日期。</p>
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanSuggestion {

    private String title;

    private String description;

    /** 建议开始时间，格式 HH:mm */
    private String suggestedStart;

    /** 建议时长（分钟） */
    private Integer duration;

    /** LOW / MEDIUM / HIGH */
    private String priority;

    private List<String> tags;

    /** AI 推荐理由，用于让用户理解「为什么这样排」 */
    private String reason;
}
