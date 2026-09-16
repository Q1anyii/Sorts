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

    /**
     * 报告当前状态。
     *
     * <p>固定为 GENERATING；命中同周期已有报告而复用时，回传该报告的真实状态
     * （可能已是 COMPLETED），前端据此决定是继续轮询还是直接展示。</p>
     */
    private String status;

    /** 预计生成时间（秒）；复用已完成报告时为 0 */
    private Integer estimatedSeconds;

    /** true 表示命中同周期已有报告、本次未重新调用模型 */
    @Builder.Default
    private boolean reused = false;
}
