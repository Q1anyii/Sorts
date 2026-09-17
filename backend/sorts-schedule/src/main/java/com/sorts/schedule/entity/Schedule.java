package com.sorts.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日程实体（对应 sorts_schedule 库 t_schedule 表）。
 *
 * <p>时长单位约定（与前端演示版、api-spec 保持一致）：</p>
 * <ul>
 *   <li>{@code plannedDuration} —— <b>分钟</b>（用户填写的计划值）；</li>
 *   <li>{@code actualDuration} —— <b>秒</b>（计时片段累加值，避免反复取整丢精度）；</li>
 *   <li>{@code t_time_record.duration} —— <b>秒</b>（单个计时片段）。</li>
 * </ul>
 *
 * @author sorts
 */
@Data
@TableName("t_schedule")
public class Schedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String description;

    /** 计划开始时间 */
    private LocalDateTime plannedStartTime;

    /** 计划时长（分钟） */
    private Integer plannedDuration;

    /** 实际开始时间（首次 start 时写入） */
    private LocalDateTime actualStartTime;

    /** 实际结束时间（end 时写入） */
    private LocalDateTime actualEndTime;

    /** 实际累计时长（秒，由所有计时片段直接求和） */
    private Integer actualDuration;

    /** 状态：见 ScheduleStatus */
    private String status;

    /** 优先级：见 SchedulePriority */
    private String priority;

    /** 标签，逗号分隔存储（如：学习,Java） */
    private String tags;

    /** 主题色（织锦配色） */
    private String color;

    private Integer deleted;

    /** 逻辑删除时间（删除操作写入，配合 deleted 做审计） */
    private LocalDateTime deletedAt;

    /** 最近一次计时状态变更的操作人（网关 X-User-Id，写操作审计） */
    private Long lastOperatorId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
