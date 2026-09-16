package com.sorts.schedule.controller;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.result.Result;
import com.sorts.schedule.dto.CalendarResponse;
import com.sorts.schedule.dto.TodayOverviewVO;
import com.sorts.schedule.dto.WeekViewResponse;
import com.sorts.schedule.service.CalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 日历接口（月历 / 周视图 / 今日概览）。
 *
 * @author sorts
 */
@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;

    /** 月历，year / month 缺省为当月 */
    @GetMapping
    public Result<CalendarResponse> month(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                          @RequestParam(value = "year", required = false) Integer year,
                                          @RequestParam(value = "month", required = false) Integer month) {
        return Result.success(calendarService.month(userId, year, month));
    }

    /** 周视图，date 缺省为今天（所在自然周：周一 → 周日） */
    @GetMapping("/week")
    public Result<WeekViewResponse> week(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                         @RequestParam(value = "date", required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(calendarService.week(userId, date));
    }

    /** 今日概览：开机第一屏 / AI 每日总结的数据源 */
    @GetMapping("/today")
    public Result<TodayOverviewVO> today(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId) {
        return Result.success(calendarService.today(userId));
    }
}
