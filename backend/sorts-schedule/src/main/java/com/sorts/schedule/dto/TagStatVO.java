package com.sorts.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标签维度统计项（对应 api-spec.json 的 TagStat）。
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagStatVO {

    /** 标签名 */
    private String tag;

    /** 该标签下日程数 */
    private Integer count;

    /** 该标签下实际投入时长（秒） */
    private Integer totalDuration;

    /** 该标签下已完成数（统计汇总口径使用） */
    private Integer completedCount;

    /** 该标签时长占比（0-1，保留四位小数） */
    private Double percentage;
}
