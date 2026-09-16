package com.sorts.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 周视图响应（GET /calendar/week）。
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeekViewResponse {

    private LocalDate weekStart;

    private LocalDate weekEnd;

    /** 周一 → 周日共 7 天 */
    private List<CalendarDayVO> days;
}
