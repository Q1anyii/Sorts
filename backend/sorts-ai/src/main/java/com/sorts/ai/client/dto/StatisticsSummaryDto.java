package com.sorts.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计汇总契约（对应 sorts-schedule 的 StatisticsSummaryVO）。
 *
 * <p>时长为<b>秒</b>；AI 侧在拼提示词时会换算成分钟，避免把「秒」直接讲给用户听。</p>
 *
 * @author sorts
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatisticsSummaryDto {

    private String period;

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer totalSchedules;

    private Integer completedSchedules;

    /** 0-1 */
    private Double completionRate;

    /** 总投入时长（秒） */
    private Integer totalFocusTime;

    /** 日均投入时长（秒） */
    private Integer avgFocusTime;

    private List<TagStatDto> tagDistribution;

    private List<TrendPointDto> dailyTrend;
}
