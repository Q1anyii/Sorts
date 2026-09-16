package com.sorts.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提醒设置实体（对应 sorts_notification 库 t_reminder_setting 表）。
 *
 * <p>每用户至多一行（唯一键 uk_user）；用户从未改过设置时表中没有记录，
 * 服务端返回默认值而不是先写一行空记录——「没有偏好」与「偏好即默认」语义不同，
 * 后者一旦调默认值就会被历史数据钉死。</p>
 *
 * @author sorts
 */
@Data
@TableName("t_reminder_setting")
public class ReminderSetting {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 默认提前提醒分钟数 */
    private Integer defaultAdvanceMinutes;

    /** 启用的渠道，逗号分隔 */
    private String channels;

    /** 是否启用免打扰时段：0 否 / 1 是 */
    private Integer quietHoursEnabled;

    /** 免打扰开始 HH:mm */
    private String quietStart;

    /** 免打扰结束 HH:mm */
    private String quietEnd;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
