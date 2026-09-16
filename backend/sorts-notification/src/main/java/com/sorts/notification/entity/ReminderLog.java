package com.sorts.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提醒留痕实体（对应 sorts_notification 库 t_reminder_log 表）。
 *
 * <p>存在的唯一理由：让「同一日程、同一提醒时刻只推一次」由数据库的唯一键来保证，
 * 而不是靠内存标记或调度器的单次执行假设。</p>
 *
 * @author sorts
 */
@Data
@TableName("t_reminder_log")
public class ReminderLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long scheduleId;

    /** 本次提醒的触发时刻（分钟取整），与 userId/scheduleId 共同构成幂等键 */
    private LocalDateTime remindAt;

    private LocalDateTime createdAt;
}
