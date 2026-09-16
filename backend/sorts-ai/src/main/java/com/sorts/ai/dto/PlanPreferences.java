package com.sorts.ai.dto;

import lombok.Data;

/**
 * 规划偏好（对应 api-spec.json 的 AIPlanRequest.preferences）。
 *
 * @author sorts
 */
@Data
public class PlanPreferences {

    /** 期望的最早开始小时（0-23），缺省 9 */
    private Integer preferredStartHour;

    /** 期望的最晚结束小时（1-24），缺省 22 */
    private Integer preferredEndHour;

    /** 未指定时长时的默认时长（分钟），缺省 60 */
    private Integer defaultDuration;
}
