package com.sorts.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 异步生成任务受理响应（对应 api-spec.json 中月度/年度总结的 202 data）。
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncReportResponse {

    private Long reportId;

    /** 固定为 GENERATING */
    private String status;

    /** 预计生成时间（秒） */
    private Integer estimatedSeconds;
}
