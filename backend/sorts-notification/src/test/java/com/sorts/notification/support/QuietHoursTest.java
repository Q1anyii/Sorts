package com.sorts.notification.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 免打扰时段判断单测：重点覆盖跨天区间与脏数据降级。
 *
 * @author sorts
 */
@DisplayName("QuietHours 免打扰时段")
class QuietHoursTest {

    @Test
    @DisplayName("parse：兼容 H:mm 与 HH:mm，非法值返回 null 而不抛异常")
    void parseIsLenient() {
        assertEquals(LocalTime.of(7, 0), QuietHours.parse("07:00"));
        assertEquals(LocalTime.of(7, 0), QuietHours.parse("7:00"));
        assertEquals(LocalTime.of(23, 30), QuietHours.parse(" 23:30 "));
        assertNull(QuietHours.parse("25:00"));
        assertNull(QuietHours.parse("晚上八点"));
        assertNull(QuietHours.parse(""));
        assertNull(QuietHours.parse(null));
    }

    @Test
    @DisplayName("同日区间：左闭右开")
    void coversSameDay() {
        assertTrue(QuietHours.covers("09:00", "12:00", LocalTime.of(9, 0)));
        assertTrue(QuietHours.covers("09:00", "12:00", LocalTime.of(10, 30)));
        // 右端不含：12:00 整点应该允许提醒
        assertFalse(QuietHours.covers("09:00", "12:00", LocalTime.of(12, 0)));
        assertFalse(QuietHours.covers("09:00", "12:00", LocalTime.of(8, 59)));
    }

    @Test
    @DisplayName("跨天区间：23:00–07:00 这类最常见的免打扰配置必须正确")
    void coversAcrossMidnight() {
        assertTrue(QuietHours.covers("23:00", "07:00", LocalTime.of(23, 30)));
        assertTrue(QuietHours.covers("23:00", "07:00", LocalTime.of(0, 0)));
        assertTrue(QuietHours.covers("23:00", "07:00", LocalTime.of(6, 59)));
        assertFalse(QuietHours.covers("23:00", "07:00", LocalTime.of(7, 0)));
        assertFalse(QuietHours.covers("23:00", "07:00", LocalTime.of(12, 0)));
        assertFalse(QuietHours.covers("23:00", "07:00", LocalTime.of(22, 59)));
    }

    @Test
    @DisplayName("起止相同视为未配置，而不是 24 小时免打扰")
    void identicalBoundsMeansDisabled() {
        assertFalse(QuietHours.covers("08:00", "08:00", LocalTime.of(8, 0)));
        assertFalse(QuietHours.covers("08:00", "08:00", LocalTime.of(20, 0)));
    }

    @Test
    @DisplayName("脏数据与空值按「不处于免打扰」处理，宁可多提醒也不静默丢失")
    void dirtyDataFailsOpen() {
        assertFalse(QuietHours.covers("25:00", "07:00", LocalTime.of(3, 0)));
        assertFalse(QuietHours.covers(null, "07:00", LocalTime.of(3, 0)));
        assertFalse(QuietHours.covers("23:00", null, LocalTime.of(3, 0)));
        assertFalse(QuietHours.covers("23:00", "07:00", null));
    }
}
