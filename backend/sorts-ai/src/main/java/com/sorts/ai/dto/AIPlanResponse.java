package com.sorts.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 规划生成响应（对应 api-spec.json 的 AIPlanResponse）。
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIPlanResponse {

    private String planId;

    private List<PlanSuggestion> suggestions;

    /** 是否已被采纳 */
    private Boolean adopted;

    /** 采纳后创建的日程ID列表 */
    private List<Long> createdScheduleIds;
}
