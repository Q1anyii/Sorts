package com.sorts.ai.dto;

import lombok.Data;

/**
 * 月度 / 年度总结请求（对应 api-spec.json 中 /ai/summary/monthly、/yearly 的请求体）。
 *
 * @author sorts
 */
@Data
public class PeriodSummaryRequest {

    /** 年度，缺省当前年 */
    private Integer year;

    /** 月度（1-12），仅月度总结使用，缺省当前月 */
    private Integer month;
}
