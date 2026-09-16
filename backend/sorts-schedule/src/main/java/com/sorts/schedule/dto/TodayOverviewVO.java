package com.sorts.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 今日概览（GET /calendar/today），也是「今日梭影」与 AI 每日总结的数据来源。
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodayOverviewVO {

    private LocalDate date;

    private Integer totalCount;

    private Integer completedCount;

    /** 穿梭中（含停梭）的数量 */
    private Integer inProgressCount;

    /** 待开始数量 */
    private Integer pendingCount;

    /** 今日已投入时长（秒） */
    private Integer focusTime;

    /** 当前穿梭中的日程，无则为 null */
    private ScheduleVO activeSchedule;

    /** 今日剩余待办日程（按计划时间升序） */
    private List<ScheduleVO> upcomingSchedules;
}
