package com.sorts.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计汇总（对应 api-spec.json 的 StatisticsSummary）。
 *
 * <p>时长单位统一为<b>秒</b>，与 Schedule.actualDuration 保持一致，
 * 前端负责按展示场景换算为分钟 / 小时。</p>
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsSummaryVO {

    /** 统计周期：day / week / month / year */
    private String period;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 总日程数 */
    private Integer totalSchedules;

    /** 已完成数 */
    private Integer completedSchedules;

    /** 完成率（0-1，保留四位小数） */
    private Double completionRate;

    /** 总投入时长（秒） */
    private Integer totalFocusTime;

    /** 日均投入时长（秒） */
    private Integer avgFocusTime;

    /** 标签分布 */
    private List<TagStatVO> tagDistribution;

    /** 按天趋势 */
    private List<TrendPointVO> dailyTrend;
}
