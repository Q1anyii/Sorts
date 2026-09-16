package com.sorts.schedule.service.impl;

import com.sorts.common.exception.BizException;
import com.sorts.schedule.dto.CalendarDayVO;
import com.sorts.schedule.dto.CalendarResponse;
import com.sorts.schedule.dto.ScheduleVO;
import com.sorts.schedule.dto.TodayOverviewVO;
import com.sorts.schedule.dto.WeekViewResponse;
import com.sorts.schedule.entity.Schedule;
import com.sorts.schedule.enums.ScheduleStatus;
import com.sorts.schedule.service.ScheduleService;
import com.sorts.schedule.service.TimerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 日历视图单元测试：月历铺格、周视图边界、今日概览口径。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalendarServiceImplTest {

    private static final Long USER_ID = 7L;

    @Mock
    private ScheduleService scheduleService;

    @Mock
    private TimerService timerService;

    @InjectMocks
    private CalendarServiceImpl calendarService;

    @BeforeEach
    void setUp() {
        when(scheduleService.listInRange(anyLong(), any(), any())).thenReturn(List.of());
        when(scheduleService.listUpcoming(anyLong(), anyInt())).thenReturn(List.of());
        when(timerService.getActive(anyLong())).thenReturn(null);
    }

    @Test
    @DisplayName("month：补齐整月每一天，含无日程的空天")
    void monthFillsEveryDay() {
        CalendarResponse response = calendarService.month(USER_ID, 2026, 9);

        assertEquals(2026, response.getYear());
        assertEquals(9, response.getMonth());
        assertEquals(30, response.getDays().size());
        assertEquals(0, response.getTotalCount());
        assertEquals(0, response.getFocusTime());
        assertEquals(LocalDate.of(2026, 9, 1), response.getDays().get(0).getDate());
        assertEquals(LocalDate.of(2026, 9, 30), response.getDays().get(29).getDate());
        // 2026-09-01 是周二 → 契约口径 dayOfWeek=2
        assertEquals(2, response.getDays().get(0).getDayOfWeek());
    }

    @Test
    @DisplayName("month：汇总数与每日数据一致，focusTime 为秒")
    void monthAggregatesTotals() {
        Schedule completed = schedule(LocalDate.of(2026, 9, 16), 3600, ScheduleStatus.COMPLETED);
        Schedule pending = schedule(LocalDate.of(2026, 9, 16), 0, ScheduleStatus.PENDING);
        when(scheduleService.listInRange(anyLong(), any(), any())).thenReturn(List.of(completed, pending));

        CalendarResponse response = calendarService.month(USER_ID, 2026, 9);

        assertEquals(2, response.getTotalCount());
        assertEquals(1, response.getCompletedCount());
        assertEquals(3600, response.getFocusTime());

        CalendarDayVO day = response.getDays().get(15);
        assertEquals(LocalDate.of(2026, 9, 16), day.getDate());
        assertEquals(2, day.getTotalCount());
        assertEquals(1, day.getCompletedCount());
        assertEquals(3600, day.getFocusTime());
        assertEquals(2, day.getSchedules().size());
    }

    @Test
    @DisplayName("month：月历不标今天（过去月份 isToday 全为 false）")
    void monthMarksTodayOnlyForCurrentMonth() {
        CalendarResponse past = calendarService.month(USER_ID, 2026, 1);
        assertTrue(past.getDays().stream().noneMatch(CalendarDayVO::getIsToday));
    }

    @Test
    @DisplayName("month：month 越界直接拒绝，不静默返回空数据")
    void monthRejectsIllegalMonth() {
        assertThrows(BizException.class, () -> calendarService.month(USER_ID, 2026, 13));
        assertThrows(BizException.class, () -> calendarService.month(USER_ID, 2026, 0));
    }

    @Test
    @DisplayName("month：只传 month 时年份取当前年，只传 year 时月份取当前月")
    void monthFillsMissingPart() {
        CalendarResponse onlyMonth = calendarService.month(USER_ID, null, 3);
        assertEquals(3, onlyMonth.getMonth());
        assertEquals(LocalDate.now().getYear(), onlyMonth.getYear());

        CalendarResponse onlyYear = calendarService.month(USER_ID, 2026, null);
        assertEquals(2026, onlyYear.getYear());
        assertEquals(LocalDate.now().getMonthValue(), onlyYear.getMonth());
    }

    @Test
    @DisplayName("week：固定周一 → 周日共 7 天")
    void weekStartsOnMonday() {
        WeekViewResponse response = calendarService.week(USER_ID, LocalDate.of(2026, 9, 16));

        assertEquals(LocalDate.of(2026, 9, 14), response.getWeekStart());
        assertEquals(LocalDate.of(2026, 9, 20), response.getWeekEnd());
        assertEquals(7, response.getDays().size());
        // 周一 → 1，周日 → 0
        assertEquals(1, response.getDays().get(0).getDayOfWeek());
        assertEquals(0, response.getDays().get(6).getDayOfWeek());
    }

    @Test
    @DisplayName("week：date 缺省为今天")
    void weekDefaultsToToday() {
        WeekViewResponse response = calendarService.week(USER_ID, null);
        LocalDate today = LocalDate.now();
        assertTrue(response.getWeekStart().getDayOfWeek().getValue() == 1);
        assertTrue(response.getDays().stream().anyMatch(d -> d.getDate().equals(today)));
    }

    @Test
    @DisplayName("today：统计今日分布，穿梭中并入 inProgressCount")
    void todayAggregatesCounts() {
        LocalDate today = LocalDate.now();
        when(scheduleService.listInRange(anyLong(), any(), any())).thenReturn(List.of(
                schedule(today, 1800, ScheduleStatus.COMPLETED),
                schedule(today, 600, ScheduleStatus.IN_PROGRESS),
                schedule(today, 300, ScheduleStatus.PAUSED),
                schedule(today, 0, ScheduleStatus.PENDING),
                schedule(today, 0, ScheduleStatus.PENDING)));

        TodayOverviewVO overview = calendarService.today(USER_ID);

        assertEquals(today, overview.getDate());
        assertEquals(5, overview.getTotalCount());
        assertEquals(1, overview.getCompletedCount());
        assertEquals(2, overview.getInProgressCount());
        assertEquals(2, overview.getPendingCount());
        assertEquals(2700, overview.getFocusTime());
        assertNull(overview.getActiveSchedule());
    }

    @Test
    @DisplayName("today：带上当前穿梭中的日程与即将开始列表")
    void todayIncludesActiveAndUpcoming() {
        LocalDate today = LocalDate.now();
        ScheduleVO active = ScheduleVO.from(schedule(today, 60, ScheduleStatus.IN_PROGRESS));
        when(timerService.getActive(USER_ID)).thenReturn(active);
        when(scheduleService.listUpcoming(anyLong(), anyInt()))
                .thenReturn(List.of(schedule(today.plusDays(1), 0, ScheduleStatus.PENDING)));

        TodayOverviewVO overview = calendarService.today(USER_ID);

        assertEquals(active, overview.getActiveSchedule());
        assertEquals(1, overview.getUpcomingSchedules().size());
    }

    @Test
    @DisplayName("today：脏状态数据不应让概览崩掉，按 PENDING 兜底")
    void todayToleratesIllegalStatus() {
        Schedule broken = schedule(LocalDate.now(), 0, ScheduleStatus.PENDING);
        broken.setStatus("WHAT");
        when(scheduleService.listInRange(anyLong(), any(), any())).thenReturn(List.of(broken));

        TodayOverviewVO overview = calendarService.today(USER_ID);

        assertEquals(1, overview.getPendingCount());
        assertEquals(1, overview.getTotalCount().intValue());
        assertEquals(0, overview.getInProgressCount().intValue());
    }

    private Schedule schedule(LocalDate date, int actualDuration, ScheduleStatus status) {
        Schedule schedule = new Schedule();
        schedule.setId(100L);
        schedule.setUserId(USER_ID);
        schedule.setTitle("织一段代码");
        schedule.setPlannedStartTime(date.atTime(9, 0));
        schedule.setActualDuration(actualDuration);
        schedule.setStatus(status.name());
        return schedule;
    }
}
