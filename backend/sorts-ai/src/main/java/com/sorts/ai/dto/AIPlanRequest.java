package com.sorts.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * AI 规划生成请求（对应 api-spec.json 的 AIPlanRequest）。
 *
 * @author sorts
 */
@Data
public class AIPlanRequest {

    @NotBlank(message = "规划描述不能为空")
    @Size(max = 2000, message = "规划描述不能超过 2000 字")
    private String userPrompt;

    /** 会话ID（可选）：规划请求同步写入对话历史，便于后续普通对话衔接上下文 */
    private String conversationId;

    /** 目标日期，缺省明天 */
    private LocalDate targetDate;

    private PlanPreferences preferences;
}
