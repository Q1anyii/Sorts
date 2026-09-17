package com.sorts.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 主计划：长时间计划容器，可挂载多个子计划（t_schedule）。
 *
 * @author sorts
 */
@Data
@TableName("t_parent_plan")
public class ParentPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String description;

    /** 主题色（十六进制） */
    private String color;

    /** 优先级：LOW / MEDIUM / HIGH / URGENT */
    private String priority;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 状态：PENDING / IN_PROGRESS / PAUSED / COMPLETED */
    private String status;

    /** 最近一次进入进行中的时间（用于计算已进行天数） */
    private LocalDateTime startedAt;

    private Integer deleted;

    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
