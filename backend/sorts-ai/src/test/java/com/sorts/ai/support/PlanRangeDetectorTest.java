package com.sorts.ai.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 相对时间范围解析单元测试。
 *
 * <p>核心契约：AI 调用层必须注入确定日期区间，禁止模型猜测日期；
 * 跨月 / 跨年边界必须正确。</p>
 *
 * @author sorts
 */
class PlanRangeDetectorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 17);

    @Test
    @DisplayName("未来一周：今天起共 7 天（start..start+6）")
    void nextWeekStartsToday() {
        var r = PlanRangeDetector.parse("帮我生成未来一周规划", TODAY);
        assertTrue(r.matched());
        assertEquals(TODAY, r.startDate());
        assertEquals(TODAY.plusDays(6), r.endDate());
    }

    @Test
    @DisplayName("一周规划 / 接下来一周 等同义表达均命中")
    void weekSynonyms() {
        assertTrue(PlanRangeDetector.parse("生成一周计划", TODAY).matched());
        assertTrue(PlanRangeDetector.parse("接下来一周的计划", TODAY).matched());
        assertTrue(PlanRangeDetector.parse("生成今天到一周后的计划", TODAY).matched());
        assertTrue(PlanRangeDetector.parse("这周计划怎么安排", TODAY).matched());
    }

    @Test
    @DisplayName("下周：下周一..周日")
    void nextCalendarWeek() {
        var r = PlanRangeDetector.parse("帮我生成下周每日计划", TODAY);
        assertTrue(r.matched());
        // 2026-09-17 是周四，下周一为 2026-09-21
        assertEquals(LocalDate.of(2026, 9, 21), r.startDate());
        assertEquals(LocalDate.of(2026, 9, 27), r.endDate());
    }

    @Test
    @DisplayName("本周：本周一..周日（按周一为一周起点）")
    void thisCalendarWeek() {
        var r = PlanRangeDetector.parse("本周计划", TODAY);
        assertEquals(LocalDate.of(2026, 9, 14), r.startDate());
        assertEquals(LocalDate.of(2026, 9, 20), r.endDate());
    }

    @Test
    @DisplayName("本月：1 号..月末")
    void thisMonth() {
        var r = PlanRangeDetector.parse("帮我规划本月", TODAY);
        assertEquals(LocalDate.of(2026, 9, 1), r.startDate());
        assertEquals(LocalDate.of(2026, 9, 30), r.endDate());
    }

    @Test
    @DisplayName("明天：单日区间（start=end）")
    void tomorrowIsSingleDay() {
        var r = PlanRangeDetector.parse("帮我规划明天的日程", TODAY);
        assertTrue(r.matched());
        assertEquals(TODAY.plusDays(1), r.startDate());
        assertEquals(TODAY.plusDays(1), r.endDate());
    }

    @Test
    @DisplayName("今天：单日区间")
    void todayIsSingleDay() {
        var r = PlanRangeDetector.parse("今天要做什么", TODAY);
        assertEquals(TODAY, r.startDate());
        assertEquals(TODAY, r.endDate());
    }

    @Test
    @DisplayName("跨月：未来一周跨入下月时 endDate 正确进位")
    void crossMonthBoundary() {
        LocalDate lateMonth = LocalDate.of(2026, 9, 28);
        var r = PlanRangeDetector.parse("未来一周", lateMonth);
        assertEquals(LocalDate.of(2026, 10, 4), r.endDate());
    }

    @Test
    @DisplayName("跨年：12 月末未来一周跨入次年")
    void crossYearBoundary() {
        LocalDate yearEnd = LocalDate.of(2026, 12, 28);
        var r = PlanRangeDetector.parse("未来一周", yearEnd);
        assertEquals(LocalDate.of(2027, 1, 3), r.endDate());
    }

    @Test
    @DisplayName("大月月末：本月 endDate 取当月实际天数")
    void monthLengthHonored() {
        var r = PlanRangeDetector.parse("本月", LocalDate.of(2026, 12, 15));
        assertEquals(LocalDate.of(2026, 12, 31), r.endDate());
    }

    @Test
    @DisplayName("无相对时间意图：不命中，由调用方回退默认单日")
    void unmatchedFallsBack() {
        assertFalse(PlanRangeDetector.parse("帮我总结一下这周的工作", TODAY).matched());
        assertFalse(PlanRangeDetector.parse(null, TODAY).matched());
        assertFalse(PlanRangeDetector.parse("", TODAY).matched());
    }
}
