package com.sorts.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 日历单日聚合数据（对应 api-spec.json 的 CalendarDay）。
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarDayVO {

    private LocalDate date;

    /** 星期：0=周日、1=周一 … 6=周六（与 api-spec 一致，前端可直接映射表头） */
    private Integer dayOfWeek;

    /** 是否为今天（前端高亮用） */
    private Boolean isToday;

    /** 当日日程总数 */
    private Integer totalCount;

    /** 当日已完成数 */
    private Integer completedCount;

    /** 当日实际投入时长（秒） */
    private Integer focusTime;

    private List<ScheduleVO> schedules;
}
