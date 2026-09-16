package com.sorts.schedule.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 日程优先级（与 api-spec.json 的 Schedule.priority 枚举一致）。
 *
 * @author sorts
 */
@Getter
@AllArgsConstructor
public enum SchedulePriority {

    LOW("低"),
    MEDIUM("中"),
    HIGH("高"),
    URGENT("紧急");

    private final String label;
}
