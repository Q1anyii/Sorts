package com.sorts.schedule.service.impl;

import com.sorts.schedule.dto.ScheduleVO;
import com.sorts.schedule.dto.StatisticsSummaryVO;
import com.sorts.schedule.dto.TagStatVO;
import com.sorts.schedule.dto.TrendPointVO;
import com.sorts.schedule.entity.Schedule;
import com.sorts.schedule.enums.ScheduleStatus;
import com.sorts.schedule.service.ScheduleService;
import com.sorts.schedule.service.StatisticsService;
import com.sorts.schedule.support.DateRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计服务实现。
 *
 * <p>口径说明：</p>
 * <ul>
 *   <li>时长单位统一为<b>秒</b>，与 Schedule.actualDuration 一致。</li>
 *   <li>{@code completionRate} = 已完成 / 总数，保留 4 位小数；无数据时为 0。</li>
 *   <li>{@code avgFocusTime} 为<b>日均</b>投入时长，分母只算「已过去的天数」，
 *       避免月初查看月统计时被未来空白天数稀释。</li>
 *   <li>标签分布按标签拆分（一条日程多个标签会分别计入），按时长降序。</li>
 * </ul>
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private static final int DEFAULT_TREND_DAYS = 30;

    private static final int MAX_TREND_DAYS = 365;

    private final ScheduleService scheduleService;

    @Override
    public StatisticsSummaryVO summary(Long userId, String period, LocalDate date) {
        DateRange range = DateRange.of(period, date);
        List<Schedule> list = scheduleService.listInRange(userId, range.start(), range.end());

        int total = list.size();
        int completed = countCompleted(list);
        int totalFocusTime = sumDuration(list);

        // 已过去的天数：统计周期未走完时只算到今天
        DateRange elapsed = range.capAt(LocalDate.now());
        long days = Math.max(1L, elapsed.days());
        // 趋势点上限保护：year / all 口径下最多回溯 365 天，避免返回超大数组
        DateRange trendRange = elapsed.days() > MAX_TREND_DAYS
                ? new DateRange(elapsed.end().minusDays(MAX_TREND_DAYS - 1L), elapsed.end())
                : elapsed;

        StatisticsSummaryVO summary = StatisticsSummaryVO.builder()
                .period(period == null || period.isBlank() ? "week" : period.trim().toLowerCase())
                .startDate(range.start())
                .endDate(range.end())
                .totalSchedules(total)
                .completedSchedules(completed)
                .completionRate(rate(completed, total))
                .totalFocusTime(totalFocusTime)
                .avgFocusTime((int) (totalFocusTime / days))
                .tagDistribution(aggregateTags(list))
                .dailyTrend(buildTrend(trendRange, list))
                .build();
        log.debug("统计汇总 userId={}, period={}, total={}, focusTime={}s", userId, summary.getPeriod(), total, totalFocusTime);
        return summary;
    }

    @Override
    public List<TrendPointVO> trend(Long userId, Integer days) {
        int size = days == null || days < 1 ? DEFAULT_TREND_DAYS : Math.min(days, MAX_TREND_DAYS);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(size - 1L);
        List<Schedule> list = scheduleService.listInRange(userId, start, end);
        return buildTrend(new DateRange(start, end), list);
    }

    @Override
    public List<TagStatVO> tags(Long userId, String period) {
        DateRange range = DateRange.of(period == null || period.isBlank() ? "month" : period, null);
        List<Schedule> list = scheduleService.listInRange(userId, range.start(), range.end());
        return aggregateTags(list);
    }

    /** 逐日补齐趋势点：无数据的日期补零，前端可直接用于绘图而无需自行补洞 */
    private List<TrendPointVO> buildTrend(DateRange range, List<Schedule> schedules) {
        Map<LocalDate, int[]> buckets = new LinkedHashMap<>();
        for (LocalDate date = range.start(); !date.isAfter(range.end()); date = date.plusDays(1)) {
            buckets.put(date, new int[]{0, 0, 0});
        }
        for (Schedule schedule : schedules) {
            if (schedule.getPlannedStartTime() == null) {
                continue;
            }
            int[] bucket = buckets.get(schedule.getPlannedStartTime().toLocalDate());
            if (bucket == null) {
                // 跨区间日程（计划时间在统计期外）忽略，保证趋势与区间口径一致
                continue;
            }
            bucket[0]++;
            if (isCompleted(schedule)) {
                bucket[1]++;
            }
            bucket[2] += schedule.getActualDuration() == null ? 0 : schedule.getActualDuration();
        }

        List<TrendPointVO> points = new ArrayList<>(buckets.size());
        buckets.forEach((date, bucket) -> points.add(TrendPointVO.builder()
                .date(date)
                .total(bucket[0])
                .completed(bucket[1])
                .totalDuration(bucket[2])
                .completionRate(rate(bucket[1], bucket[0]))
                .build()));
        return points;
    }

    /** 标签聚合：一条日程的多个标签分别计入各自条目 */
    private List<TagStatVO> aggregateTags(List<Schedule> schedules) {
        Map<String, int[]> buckets = new LinkedHashMap<>();
        for (Schedule schedule : schedules) {
            for (String tag : ScheduleVO.splitTags(schedule.getTags())) {
                int[] bucket = buckets.computeIfAbsent(tag, k -> new int[]{0, 0, 0});
                bucket[0]++;
                bucket[1] += schedule.getActualDuration() == null ? 0 : schedule.getActualDuration();
                if (isCompleted(schedule)) {
                    bucket[2]++;
                }
            }
        }

        int grandTotal = buckets.values().stream().mapToInt(b -> b[1]).sum();
        List<TagStatVO> stats = new ArrayList<>(buckets.size());
        buckets.forEach((tag, bucket) -> stats.add(TagStatVO.builder()
                .tag(tag)
                .count(bucket[0])
                .totalDuration(bucket[1])
                .completedCount(bucket[2])
                .percentage(grandTotal == 0 ? 0D : rate(bucket[1], grandTotal))
                .build()));
        stats.sort(Comparator.comparingInt(TagStatVO::getTotalDuration).reversed()
                .thenComparing(TagStatVO::getTag));
        return stats;
    }

    private int countCompleted(List<Schedule> schedules) {
        return (int) schedules.stream().filter(this::isCompleted).count();
    }

    private int sumDuration(List<Schedule> schedules) {
        return schedules.stream()
                .mapToInt(s -> s.getActualDuration() == null ? 0 : s.getActualDuration())
                .sum();
    }

    private boolean isCompleted(Schedule schedule) {
        return ScheduleStatus.COMPLETED.name().equals(schedule.getStatus());
    }

    /** 比率统一保留 4 位小数，避免浮点尾数污染前端展示 */
    private double rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
