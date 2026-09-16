package com.sorts.schedule.service.impl;

import com.sorts.common.exception.BizException;
import com.sorts.schedule.dto.StatisticsSummaryVO;
import com.sorts.schedule.dto.TagStatVO;
import com.sorts.schedule.dto.TrendPointVO;
import com.sorts.schedule.entity.Schedule;
import com.sorts.schedule.enums.ScheduleStatus;
import com.sorts.schedule.service.ScheduleService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 统计服务单元测试：汇总口径、趋势补洞、标签分布。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatisticsServiceImplTest {

    private static final Long USER_ID = 7L;

    @Mock
    private ScheduleService scheduleService;

    @InjectMocks
    private StatisticsServiceImpl statisticsService;

    /** 取一个已完整走完的月份，保证「已过去天数」等于整月天数，断言可预期 */
    private LocalDate pastMonthAnchor;

    @BeforeEach
    void setUp() {
        pastMonthAnchor = LocalDate.now().minusMonths(2);
        when(scheduleService.listInRange(anyLong(), any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("summary：区间、完成率、总时长与日均口径")
    void summaryComputesCoreMetrics() {
        LocalDate day = pastMonthAnchor.withDayOfMonth(5);
        when(scheduleService.listInRange(anyLong(), any(), any())).thenReturn(List.of(
                schedule(day, 3600, ScheduleStatus.COMPLETED, "学习,Java"),
                schedule(day, 1800, ScheduleStatus.COMPLETED, "学习"),
                schedule(day.plusDays(1), 0, ScheduleStatus.PENDING, "学习")));

        StatisticsSummaryVO summary = statisticsService.summary(USER_ID, "month", pastMonthAnchor);

        assertEquals("month", summary.getPeriod());
        assertEquals(pastMonthAnchor.withDayOfMonth(1), summary.getStartDate());
        assertEquals(pastMonthAnchor.withDayOfMonth(pastMonthAnchor.lengthOfMonth()), summary.getEndDate());
        assertEquals(3, summary.getTotalSchedules().intValue());
        assertEquals(2, summary.getCompletedSchedules().intValue());
        // 2/3 = 0.6667（保留 4 位小数）
        assertEquals(0.6667, summary.getCompletionRate(), 0.00001);
        assertEquals(5400, summary.getTotalFocusTime().intValue());
        // 日均：整月已过去，分母为当月天数
        assertEquals(5400 / pastMonthAnchor.lengthOfMonth(), summary.getAvgFocusTime().intValue());
    }

    @Test
    @DisplayName("summary：day 粒度下日均等于当日总时长")
    void summaryForSingleDay() {
        LocalDate today = LocalDate.now();
        when(scheduleService.listInRange(anyLong(), any(), any()))
                .thenReturn(List.of(schedule(today, 900, ScheduleStatus.COMPLETED, "学习")));

        StatisticsSummaryVO summary = statisticsService.summary(USER_ID, "day", today);

        assertEquals(900, summary.getTotalFocusTime().intValue());
        assertEquals(900, summary.getAvgFocusTime().intValue());
        assertEquals(1.0, summary.getCompletionRate(), 0.00001);
        assertEquals(1, summary.getDailyTrend().size());
    }

    @Test
    @DisplayName("summary：无数据时完成率为 0，不出现除零异常")
    void summaryHandlesEmpty() {
        StatisticsSummaryVO summary = statisticsService.summary(USER_ID, "month", pastMonthAnchor);

        assertEquals(0, summary.getTotalSchedules().intValue());
        assertEquals(0.0, summary.getCompletionRate(), 0.00001);
        assertEquals(0, summary.getTotalFocusTime().intValue());
        assertTrue(summary.getTagDistribution().isEmpty());
        assertEquals(pastMonthAnchor.lengthOfMonth(), summary.getDailyTrend().size());
    }

    @Test
    @DisplayName("summary：period 非法直接拒绝")
    void summaryRejectsIllegalPeriod() {
        assertThrows(BizException.class, () -> statisticsService.summary(USER_ID, "quarter", LocalDate.now()));
    }

    @Test
    @DisplayName("summary：dailyTrend 覆盖区间每一天且补零，前端可直接绘图")
    void summaryFillsTrendGaps() {
        LocalDate day = pastMonthAnchor.withDayOfMonth(3);
        when(scheduleService.listInRange(anyLong(), any(), any()))
                .thenReturn(List.of(schedule(day, 600, ScheduleStatus.COMPLETED, "学习")));

        StatisticsSummaryVO summary = statisticsService.summary(USER_ID, "month", pastMonthAnchor);

        List<TrendPointVO> trend = summary.getDailyTrend();
        assertEquals(pastMonthAnchor.lengthOfMonth(), trend.size());
        TrendPointVO filled = trend.get(0);
        assertEquals(pastMonthAnchor.withDayOfMonth(1), filled.getDate());
        assertEquals(0, filled.getTotal().intValue());
        assertEquals(0, filled.getTotalDuration().intValue());

        TrendPointVO hit = trend.get(2);
        assertEquals(1, hit.getTotal().intValue());
        assertEquals(1, hit.getCompleted().intValue());
        assertEquals(600, hit.getTotalDuration().intValue());
        assertEquals(1.0, hit.getCompletionRate(), 0.00001);
    }

    @Test
    @DisplayName("trend：缺省 30 天，含今天且按日期升序")
    void trendDefaultsTo30Days() {
        List<TrendPointVO> trend = statisticsService.trend(USER_ID, null);

        assertEquals(30, trend.size());
        assertEquals(LocalDate.now().minusDays(29), trend.get(0).getDate());
        assertEquals(LocalDate.now(), trend.get(29).getDate());
    }

    @Test
    @DisplayName("trend：天数上限 365，防御超大查询")
    void trendCapsDays() {
        assertEquals(365, statisticsService.trend(USER_ID, 9999).size());
        assertEquals(1, statisticsService.trend(USER_ID, 1).size());
        // 非法天数回落到缺省值
        assertEquals(30, statisticsService.trend(USER_ID, 0).size());
    }

    @Test
    @DisplayName("trend：区间外的日程不计入趋势点")
    void trendIgnoresOutOfRangeSchedules() {
        when(scheduleService.listInRange(anyLong(), any(), any()))
                .thenReturn(List.of(schedule(LocalDate.now().minusDays(100), 1200, ScheduleStatus.COMPLETED, "学习")));

        List<TrendPointVO> trend = statisticsService.trend(USER_ID, 7);

        assertEquals(7, trend.size());
        assertTrue(trend.stream().allMatch(p -> p.getTotalDuration() == 0));
    }

    @Test
    @DisplayName("tags：按时长降序、占比归一、单标签多次出现只计一条日程一次")
    void tagsAggregateAndSort() {
        when(scheduleService.listInRange(anyLong(), any(), any())).thenReturn(List.of(
                schedule(LocalDate.now(), 3600, ScheduleStatus.COMPLETED, "学习,Java"),
                schedule(LocalDate.now(), 1800, ScheduleStatus.COMPLETED, "学习"),
                schedule(LocalDate.now(), 600, ScheduleStatus.PENDING, "运动")));

        List<TagStatVO> tags = statisticsService.tags(USER_ID, "all");

        assertEquals(3, tags.size());
        assertEquals("学习", tags.get(0).getTag());
        assertEquals(2, tags.get(0).getCount().intValue());
        assertEquals(5400, tags.get(0).getTotalDuration().intValue());
        assertEquals(2, tags.get(0).getCompletedCount().intValue());
        assertEquals("Java", tags.get(1).getTag());
        assertEquals("运动", tags.get(2).getTag());
        // 占比之和为 1（总时长 5400 + 3600 + 600 = 9600）
        double sum = tags.stream().mapToDouble(TagStatVO::getPercentage).sum();
        assertEquals(1.0, sum, 0.0001);
    }

    @Test
    @DisplayName("tags：无标签日程被忽略，全为空时不报错")
    void tagsSkipsUntagged() {
        when(scheduleService.listInRange(anyLong(), any(), any()))
                .thenReturn(List.of(schedule(LocalDate.now(), 600, ScheduleStatus.COMPLETED, null)));

        assertTrue(statisticsService.tags(USER_ID, "month").isEmpty());
    }

    @Test
    @DisplayName("tags：period 非法直接拒绝")
    void tagsRejectsIllegalPeriod() {
        assertThrows(BizException.class, () -> statisticsService.tags(USER_ID, "quarter"));
    }

    private Schedule schedule(LocalDate date, int actualDuration, ScheduleStatus status, String tags) {
        Schedule schedule = new Schedule();
        schedule.setId((long) (Math.random() * 10000));
        schedule.setUserId(USER_ID);
        schedule.setTitle("织一段代码");
        schedule.setPlannedStartTime(date.atTime(9, 0));
        schedule.setActualDuration(actualDuration);
        schedule.setStatus(status.name());
        schedule.setTags(tags);
        return schedule;
    }
}
