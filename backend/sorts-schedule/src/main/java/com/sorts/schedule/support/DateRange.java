package com.sorts.schedule.support;

import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 统计期 → 日期区间解析（日历视图与统计服务共用，保证口径一致）。
 *
 * <p>周口径固定为「周一 → 周日」，与前端周视图表头一致。</p>
 *
 * @author sorts
 */
public record DateRange(LocalDate start, LocalDate end) {

    /** 支持的时间粒度 */
    private static final String PERIOD_DAY = "day";

    private static final String PERIOD_WEEK = "week";

    private static final String PERIOD_MONTH = "month";

    private static final String PERIOD_YEAR = "year";

    /** 「全部」粒度：用于标签分布等不限定周期的场景 */
    private static final String PERIOD_ALL = "all";

    /**
     * 按粒度解析区间。
     *
     * @param period 粒度：day / week / month / year / all，非法值直接拒绝
     * @param anchor 锚点日期，null 取今天
     */
    public static DateRange of(String period, LocalDate anchor) {
        String normalized = period == null || period.isBlank() ? PERIOD_WEEK : period.trim().toLowerCase();
        LocalDate date = anchor == null ? LocalDate.now() : anchor;
        return switch (normalized) {
            case PERIOD_DAY -> new DateRange(date, date);
            case PERIOD_WEEK -> weekOf(date);
            case PERIOD_MONTH -> {
                LocalDate first = date.withDayOfMonth(1);
                yield new DateRange(first, first.withDayOfMonth(first.lengthOfMonth()));
            }
            case PERIOD_YEAR -> new DateRange(date.withDayOfYear(1), date.withDayOfYear(date.lengthOfYear()));
            // 全部：用一个足够宽的区间表达「不设限」，调用方无需再处理 null 边界
            case PERIOD_ALL -> new DateRange(LocalDate.of(1970, 1, 1), LocalDate.of(2999, 12, 31));
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "period 取值非法：" + period);
        };
    }

    /** 指定日期所在自然周（周一 → 周日） */
    public static DateRange weekOf(LocalDate date) {
        LocalDate monday = date.with(DayOfWeek.MONDAY);
        return new DateRange(monday, monday.plusDays(6));
    }

    /** 区间总天数（含首尾） */
    public long days() {
        return end.toEpochDay() - start.toEpochDay() + 1;
    }

    /**
     * 用上界裁剪区间。
     *
     * <p>用于「日均」口径：统计周期尚未走完时，不应把未来空白天数计入分母。</p>
     */
    public DateRange capAt(LocalDate limit) {
        if (limit == null || !end.isAfter(limit)) {
            return this;
        }
        return new DateRange(start, limit);
    }

    /** 区间是否包含指定日期 */
    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
