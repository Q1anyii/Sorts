package com.sorts.schedule.service;

import com.sorts.schedule.dto.StatisticsSummaryVO;
import com.sorts.schedule.dto.TagStatVO;
import com.sorts.schedule.dto.TrendPointVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计服务：汇总 / 趋势 / 标签分布。
 *
 * @author sorts
 */
public interface StatisticsService {

    /**
     * 统计汇总。
     *
     * @param period day / week / month / year，缺省 week
     * @param date   锚点日期，缺省今天
     */
    StatisticsSummaryVO summary(Long userId, String period, LocalDate date);

    /**
     * 最近 N 天趋势（含今天，空天补零，便于直接绘图）。
     *
     * @param days 天数，缺省 30，上限 365
     */
    List<TrendPointVO> trend(Long userId, Integer days);

    /**
     * 标签分布（按时长降序，含占比）。
     *
     * @param period day / week / month / year / all，缺省 month
     */
    List<TagStatVO> tags(Long userId, String period);
}
