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

    /**
     * 是否强制重新生成（默认 false = 命中同周期已有报告时直接复用）。
     *
     * <p>同周期报告默认幂等：同一用户对同一月份 / 年度重复点击不会重复调模型、
     * 也不会堆出多条记录。确需刷新（例如当月又补记了日程）时传 true。</p>
     */
    private Boolean force;
}
