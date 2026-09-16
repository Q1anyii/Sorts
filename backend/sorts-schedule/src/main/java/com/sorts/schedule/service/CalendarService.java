package com.sorts.schedule.service;

import com.sorts.schedule.dto.CalendarResponse;
import com.sorts.schedule.dto.TodayOverviewVO;
import com.sorts.schedule.dto.WeekViewResponse;

import java.time.LocalDate;

/**
 * 日历视图服务：月历 / 周视图 / 今日概览。
 *
 * @author sorts
 */
public interface CalendarService {

    /** 月历：返回该月每一天的聚合（含空天，前端可直接铺网格） */
    CalendarResponse month(Long userId, Integer year, Integer month);

    /** 周视图：以指定日期所在自然周（周一 → 周日） */
    WeekViewResponse week(Long userId, LocalDate date);

    /** 今日概览：今日分布 + 当前穿梭中的日程 + 即将开始 */
    TodayOverviewVO today(Long userId);
}
