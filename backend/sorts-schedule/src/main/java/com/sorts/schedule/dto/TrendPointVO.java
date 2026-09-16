package com.sorts.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 趋势数据点（按天），同时服务于统计汇总的 dailyTrend 与 /statistics/trend。
 *
 * <p>字段取两者并集：{@code total} / {@code completionRate} 用于独立的趋势接口，
 * 统计汇总侧不填即为 null，不影响契约兼容性。</p>
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendPointVO {

    private LocalDate date;

    /** 当日日程总数 */
    private Integer total;

    /** 当日完成数 */
    private Integer completed;

    /** 当日实际投入时长（秒） */
    private Integer totalDuration;

    /** 当日完成率（0-1，保留四位小数） */
    private Double completionRate;
}
