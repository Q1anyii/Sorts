package com.sorts.notification.dto;

import lombok.Data;

import java.util.List;

/**
 * 提醒设置视图对象（字段与 api-spec.json 的 /notifications/settings 出参一致）。
 *
 * @author sorts
 */
@Data
public class ReminderSettingVO {

    /** 默认提前提醒分钟数 */
    private Integer defaultAdvanceMinutes;

    /** 启用的提醒渠道 */
    private List<String> channels;

    /** 是否启用免打扰时段 */
    private Boolean quietHoursEnabled;

    /** 免打扰开始 HH:mm */
    private String quietStart;

    /** 免打扰结束 HH:mm */
    private String quietEnd;

    /** 该用户是否已自定义过设置（false 表示返回的是服务端默认值） */
    private Boolean customized;
}
