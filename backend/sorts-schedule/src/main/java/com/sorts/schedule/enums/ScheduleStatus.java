package com.sorts.schedule.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * 日程状态（与 api-spec.json 的 Schedule.status 枚举保持一致）。
 *
 * <p>合法流转：</p>
 * <pre>
 * PENDING ──start──► IN_PROGRESS ──pause──► PAUSED ──resume──► IN_PROGRESS
 *    │                   │                     │
 *    │                   └──────end────────────┴──► COMPLETED
 *    ├──────────cancel──────────► CANCELLED ◄──────┘
 *    └──(计划时间已过未开始)──► TIMEOUT
 * </pre>
 *
 * @author sorts
 */
@Getter
@AllArgsConstructor
public enum ScheduleStatus {

    /** 待开始 */
    PENDING("待开始"),

    /** 穿梭中（计时进行中） */
    IN_PROGRESS("穿梭中"),

    /** 已暂停（停梭） */
    PAUSED("已暂停"),

    /** 已落梭（完成） */
    COMPLETED("已完成"),

    /** 已取消 */
    CANCELLED("已取消"),

    /** 已超时（计划时间已过仍未开始） */
    TIMEOUT("已超时");

    private final String label;

    /** 未进入终态的日程可被计时操作，终态不可再流转 */
    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED || this == TIMEOUT;
    }

    /** 允许开始计时的前置状态 */
    public static Set<ScheduleStatus> startableFrom() {
        return EnumSet.of(PENDING, PAUSED);
    }

    /** 允许暂停的前置状态 */
    public static Set<ScheduleStatus> pausableFrom() {
        return EnumSet.of(IN_PROGRESS);
    }

    /** 允许结束的前置状态（PENDING 未开始也可直接结束，按 0 时长处理） */
    public static Set<ScheduleStatus> endableFrom() {
        return EnumSet.of(PENDING, IN_PROGRESS, PAUSED);
    }

    /** 允许取消的前置状态 */
    public static Set<ScheduleStatus> cancellableFrom() {
        return EnumSet.of(PENDING, IN_PROGRESS, PAUSED, TIMEOUT);
    }
}
