package com.sorts.notification.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 提醒设置更新请求（字段与 api-spec.json 的 /notifications/settings 入参一致）。
 *
 * @author sorts
 */
@Data
public class ReminderSettingRequest {

    /** 提前提醒分钟数：1 分钟 ~ 24 小时，过大等价于提前一天提醒，没有使用价值 */
    @Min(value = 1, message = "提前提醒不能少于 1 分钟")
    @Max(value = 1440, message = "提前提醒不能超过 1440 分钟")
    private Integer defaultAdvanceMinutes;

    /** 启用的渠道：APP/EMAIL/SMS */
    private List<String> channels;

    private Boolean quietHoursEnabled;

    /** HH:mm，允许为空（未启用免打扰时不校验） */
    @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "免打扰时间需为 HH:mm 格式")
    private String quietStart;

    @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "免打扰时间需为 HH:mm 格式")
    private String quietEnd;
}
