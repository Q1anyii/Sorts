package com.sorts.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 月历视图响应（GET /calendar）。
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarResponse {

    private Integer year;

    private Integer month;

    /** 该月每一天的聚合数据（含无日程的空天，便于前端直接渲染网格） */
    private List<CalendarDayVO> days;

    /** 该月日程总数 */
    private Integer totalCount;

    /** 该月已完成数 */
    private Integer completedCount;

    /** 该月实际投入时长（秒） */
    private Integer focusTime;
}
