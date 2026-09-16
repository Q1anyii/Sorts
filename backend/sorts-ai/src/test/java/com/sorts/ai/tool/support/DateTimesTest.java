package com.sorts.ai.tool.support;

import com.sorts.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 时间容错解析单元测试。
 *
 * <p>用例全部来自模型真实会输出的写法，而不是「标准格式」——
 * 标准格式本来就一定能过，测它的价值很低。</p>
 *
 * @author sorts
 */
class DateTimesTest {

    @Test
    @DisplayName("日期：支持短横线与斜杠两种分隔")
    void parseDateVariants() {
        assertEquals(LocalDate.of(2026, 9, 16), DateTimes.parseDate("2026-09-16", "date"));
        assertEquals(LocalDate.of(2026, 9, 16), DateTimes.parseDate("2026/09/16", "date"));
        assertEquals(LocalDate.of(2026, 9, 16), DateTimes.parseDate("  2026-09-16  ", "date"));
    }

    @Test
    @DisplayName("日期：空白与 null 都视为未提供")
    void parseDateEmpty() {
        assertNull(DateTimes.parseDate(null, "date"));
        assertNull(DateTimes.parseDate("   ", "date"));
    }

    @Test
    @DisplayName("日期非法：抛出带字段名的参数错误")
    void parseDateInvalid() {
        BizException error = assertThrows(BizException.class, () -> DateTimes.parseDate("明天", "date"));
        assertEquals(400, error.getCode());
        assertEquals("date 不是合法日期：明天", error.getMessage());
    }

    @Test
    @DisplayName("时间：ISO、空格分隔（含/不含秒）三种写法都能解析")
    void parseDateTimeVariants() {
        LocalDateTime expected = LocalDateTime.of(2026, 9, 16, 9, 0);
        assertEquals(expected, DateTimes.parseDateTime("2026-09-16T09:00:00", "t"));
        assertEquals(expected, DateTimes.parseDateTime("2026-09-16 09:00:00", "t"));
        assertEquals(expected, DateTimes.parseDateTime("2026-09-16 09:00", "t"));
        assertEquals(expected, DateTimes.parseDateTime("2026/09/16 09:00", "t"));
    }

    @Test
    @DisplayName("时间非法：提示建议格式，便于模型自我纠正")
    void parseDateTimeInvalid() {
        BizException error = assertThrows(BizException.class, () -> DateTimes.parseDateTime("上午九点", "plannedStartTime"));
        assertEquals(400, error.getCode());
        assertEquals("plannedStartTime 不是合法时间（建议 yyyy-MM-dd HH:mm:ss）：上午九点", error.getMessage());
    }

    @Test
    @DisplayName("建议时间落到目标日期：HH:mm 与 H:mm 都可")
    void atTimeOn() {
        LocalDate date = LocalDate.of(2026, 9, 17);
        assertEquals(LocalDateTime.of(2026, 9, 17, 9, 30), DateTimes.atTimeOn(date, "09:30"));
        assertEquals(LocalDateTime.of(2026, 9, 17, 9, 30), DateTimes.atTimeOn(date, "9:30"));
        assertNull(DateTimes.atTimeOn(date, null));
    }
}
