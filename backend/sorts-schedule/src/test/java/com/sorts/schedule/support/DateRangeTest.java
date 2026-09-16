package com.sorts.schedule.support;

import com.sorts.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统计区间解析单元测试。
 *
 * @author sorts
 */
class DateRangeTest {

    @Test
    @DisplayName("day：区间锁定在锚点当天")
    void dayPeriod() {
        DateRange range = DateRange.of("day", LocalDate.of(2026, 9, 16));
        assertEquals(LocalDate.of(2026, 9, 16), range.start());
        assertEquals(LocalDate.of(2026, 9, 16), range.end());
        assertEquals(1L, range.days());
    }

    @Test
    @DisplayName("week：周一 → 周日（周日锚点也归入本周周一）")
    void weekPeriod() {
        // 2026-09-16 是周三
        DateRange wednesday = DateRange.of("week", LocalDate.of(2026, 9, 16));
        assertEquals(LocalDate.of(2026, 9, 14), wednesday.start());
        assertEquals(LocalDate.of(2026, 9, 20), wednesday.end());
        assertEquals(7L, wednesday.days());

        // 2026-09-20 是周日，应回到 09-14 那一周，而不是顺延到下周
        DateRange sunday = DateRange.of("week", LocalDate.of(2026, 9, 20));
        assertEquals(LocalDate.of(2026, 9, 14), sunday.start());
        assertEquals(LocalDate.of(2026, 9, 20), sunday.end());
    }

    @Test
    @DisplayName("month：自然月首尾，闰年二月按 29 天")
    void monthPeriod() {
        DateRange february = DateRange.of("month", LocalDate.of(2024, 2, 10));
        assertEquals(LocalDate.of(2024, 2, 1), february.start());
        assertEquals(LocalDate.of(2024, 2, 29), february.end());
        assertEquals(29L, february.days());
    }

    @Test
    @DisplayName("year：1 月 1 日 → 12 月 31 日")
    void yearPeriod() {
        DateRange range = DateRange.of("year", LocalDate.of(2026, 6, 1));
        assertEquals(LocalDate.of(2026, 1, 1), range.start());
        assertEquals(LocalDate.of(2026, 12, 31), range.end());
    }

    @Test
    @DisplayName("period 为空按 week，非法值直接拒绝")
    void defaultAndIllegal() {
        LocalDate anchor = LocalDate.of(2026, 9, 16);
        assertEquals(DateRange.weekOf(anchor), DateRange.of(null, anchor));
        assertEquals(DateRange.weekOf(anchor), DateRange.of("  ", anchor));
        assertThrows(BizException.class, () -> DateRange.of("quarter", anchor));
    }

    @Test
    @DisplayName("all：用宽区间表达「不设限」，避免调用方处理 null 边界")
    void allPeriod() {
        DateRange range = DateRange.of("all", LocalDate.now());
        assertTrue(range.start().isBefore(LocalDate.of(2000, 1, 1)));
        assertTrue(range.end().isAfter(LocalDate.of(2100, 1, 1)));
    }

    @Test
    @DisplayName("capAt：统计周期未走完时只裁剪上界，保证「日均」分母正确")
    void capAtLimit() {
        DateRange month = DateRange.of("month", LocalDate.of(2026, 9, 1));
        DateRange capped = month.capAt(LocalDate.of(2026, 9, 16));
        assertEquals(LocalDate.of(2026, 9, 1), capped.start());
        assertEquals(LocalDate.of(2026, 9, 16), capped.end());
        assertEquals(16L, capped.days());

        // 上界晚于区间末尾时保持原样
        assertEquals(month, month.capAt(LocalDate.of(2027, 1, 1)));
    }

    @Test
    @DisplayName("contains：区间边界闭包含首尾")
    void containsDate() {
        DateRange range = DateRange.weekOf(LocalDate.of(2026, 9, 16));
        assertTrue(range.contains(LocalDate.of(2026, 9, 14)));
        assertTrue(range.contains(LocalDate.of(2026, 9, 20)));
        assertTrue(!range.contains(LocalDate.of(2026, 9, 21)));
    }
}
