package com.sorts.schedule.service.impl;

import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.schedule.dto.CalendarDayVO;
import com.sorts.schedule.dto.CalendarResponse;
import com.sorts.schedule.dto.ScheduleVO;
import com.sorts.schedule.dto.TodayOverviewVO;
import com.sorts.schedule.dto.WeekViewResponse;
import com.sorts.schedule.entity.Schedule;
import com.sorts.schedule.enums.ScheduleStatus;
import com.sorts.schedule.service.CalendarService;
import com.sorts.schedule.service.ScheduleService;
import com.sorts.schedule.service.TimerService;
import com.sorts.schedule.support.DateRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 日历视图实现。
 *
 * <p>聚合策略：一次取回区间内全部日程后在内存按「计划开始时间所在自然日」分组，
 * 避免为每一天发一条 SQL（N+1）。个人日程量级下这是最简且最稳的做法。</p>
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarServiceImpl implements CalendarService {

    private final ScheduleService scheduleService;

    private final TimerService timerService;

    @Override
    public CalendarResponse month(Long userId, Integer year, Integer month) {
        YearMonth yearMonth = resolveYearMonth(year, month);
        LocalDate first = yearMonth.atDay(1);
        LocalDate last = yearMonth.atEndOfMonth();

        Map<LocalDate, List<Schedule>> grouped = groupByDate(
                scheduleService.listInRange(userId, first, last));

        List<CalendarDayVO> days = new ArrayList<>(yearMonth.lengthOfMonth());
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            days.add(buildDay(date, grouped.get(date)));
        }

        return CalendarResponse.builder()
                .year(yearMonth.getYear())
                .month(yearMonth.getMonthValue())
                .days(days)
                .totalCount(days.stream().mapToInt(CalendarDayVO::getTotalCount).sum())
                .completedCount(days.stream().mapToInt(CalendarDayVO::getCompletedCount).sum())
                .focusTime(days.stream().mapToInt(CalendarDayVO::getFocusTime).sum())
                .build();
    }

    @Override
    public WeekViewResponse week(Long userId, LocalDate date) {
        DateRange range = DateRange.weekOf(date == null ? LocalDate.now() : date);
        Map<LocalDate, List<Schedule>> grouped = groupByDate(
                scheduleService.listInRange(userId, range.start(), range.end()));

        List<CalendarDayVO> days = new ArrayList<>(7);
        for (LocalDate cursor = range.start(); !cursor.isAfter(range.end()); cursor = cursor.plusDays(1)) {
            days.add(buildDay(cursor, grouped.get(cursor)));
        }
        return WeekViewResponse.builder()
                .weekStart(range.start())
                .weekEnd(range.end())
                .days(days)
                .build();
    }

    @Override
    public TodayOverviewVO today(Long userId) {
        LocalDate today = LocalDate.now();
        List<Schedule> todayList = scheduleService.listInRange(userId, today, today);

        int completed = 0;
        int inProgress = 0;
        int pending = 0;
        int focusTime = 0;
        for (Schedule schedule : todayList) {
            ScheduleStatus status = parseStatus(schedule);
            if (status == ScheduleStatus.COMPLETED) {
                completed++;
            }
            if (status == ScheduleStatus.IN_PROGRESS || status == ScheduleStatus.PAUSED) {
                inProgress++;
            }
            if (status == ScheduleStatus.PENDING) {
                pending++;
            }
            focusTime += schedule.getActualDuration() == null ? 0 : schedule.getActualDuration();
        }

        List<ScheduleVO> upcoming = scheduleService.listUpcoming(userId, 5).stream()
                .map(ScheduleVO::from)
                .toList();

        TodayOverviewVO overview = TodayOverviewVO.builder()
                .date(today)
                .totalCount(todayList.size())
                .completedCount(completed)
                .inProgressCount(inProgress)
                .pendingCount(pending)
                .focusTime(focusTime)
                .activeSchedule(timerService.getActive(userId))
                .upcomingSchedules(upcoming)
                .build();
        log.debug("今日概览 userId={}, total={}, focusTime={}", userId, todayList.size(), focusTime);
        return overview;
    }

    /** 解析 year / month，缺省为当月；月份越界直接拒绝（避免前端手工拼参数出错后静默返回空数据） */
    private YearMonth resolveYearMonth(Integer year, Integer month) {
        if (year == null && month == null) {
            return YearMonth.now();
        }
        if (year != null && month != null) {
            if (month < 1 || month > 12) {
                throw new BizException(ErrorCode.PARAM_ERROR, "month 取值非法：" + month);
            }
            return YearMonth.of(year, month);
        }
        // 只传其中一个：以另一个取当前值补齐，降低前端调用成本
        YearMonth now = YearMonth.now();
        if (year == null) {
            if (month < 1 || month > 12) {
                throw new BizException(ErrorCode.PARAM_ERROR, "month 取值非法：" + month);
            }
            return YearMonth.of(now.getYear(), month);
        }
        return YearMonth.of(year, now.getMonthValue());
    }

    private Map<LocalDate, List<Schedule>> groupByDate(List<Schedule> schedules) {
        return schedules.stream()
                .filter(s -> s.getPlannedStartTime() != null)
                .collect(Collectors.groupingBy(s -> s.getPlannedStartTime().toLocalDate()));
    }

    private CalendarDayVO buildDay(LocalDate date, List<Schedule> schedules) {
        List<Schedule> list = schedules == null ? List.of() : schedules;
        int completed = 0;
        int focusTime = 0;
        for (Schedule schedule : list) {
            if (parseStatus(schedule) == ScheduleStatus.COMPLETED) {
                completed++;
            }
            focusTime += schedule.getActualDuration() == null ? 0 : schedule.getActualDuration();
        }
        return CalendarDayVO.builder()
                .date(date)
                // 契约约定 0=周日：LocalDate 的取值是 1=周一…7=周日，取模后正好对齐
                .dayOfWeek(date.getDayOfWeek().getValue() % 7)
                .isToday(date.equals(LocalDate.now()))
                .totalCount(list.size())
                .completedCount(completed)
                .focusTime(focusTime)
                .schedules(list.stream().map(ScheduleVO::from).toList())
                .build();
    }

    private ScheduleStatus parseStatus(Schedule schedule) {
        try {
            return ScheduleStatus.valueOf(schedule.getStatus());
        } catch (IllegalArgumentException | NullPointerException e) {
            // 脏数据不应让整个日历崩掉，按待开始处理并留痕
            log.warn("日程状态非法，按 PENDING 处理 scheduleId={}, status={}", schedule.getId(), schedule.getStatus());
            return ScheduleStatus.PENDING;
        }
    }
}
