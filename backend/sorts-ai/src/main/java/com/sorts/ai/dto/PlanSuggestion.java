package com.sorts.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单条规划建议（对应 api-spec.json 的 AIPlanResponse.suggestions 项）。
 *
 * <p>{@code suggestedStart} 只有时分（HH:mm）。多日规划时由模型按注入的
 * 「今天是几号 + 允许区间」自行输出 {@code date}（yyyy-MM-dd）；
 * 单日规划可缺省 {@code date}，回落到规划的 targetDate。</p>
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanSuggestion {

    /** 建议日期 yyyy-MM-dd（多日规划必填；单日规划缺省时回落到计划目标日期） */
    private String date;

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
