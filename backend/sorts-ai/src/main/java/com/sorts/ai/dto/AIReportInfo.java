package com.sorts.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报告信息（对应 api-spec.json 的 AIReportInfo）。
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIReportInfo {

    private Long id;

    private Long userId;

    /** DAILY / WEEKLY / MONTHLY / YEARLY */
    private String type;

    private String title;

    /** Markdown 正文 */
    private String content;

    private Double completionRate;

    /** 总专注时长（秒） */
    private Integer totalFocusTime;

    private List<String> highlights;

    private List<String> suggestions;

    private LocalDateTime generatedAt;

    /** GENERATING / COMPLETED / FAILED */
    private String status;
}
