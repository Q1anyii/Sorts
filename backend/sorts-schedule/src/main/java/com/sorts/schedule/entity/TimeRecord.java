package com.sorts.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 计时片段（每次 start/resume 到 pause/end 之间为一段）。
 *
 * <p>保存分段记录而非只存总时长，可支撑「今日梭影」中的时间分布回溯，
 * 也让暂停/继续的累加逻辑天然幂等。</p>
 *
 * @author sorts
 */
@Data
@TableName("t_time_record")
public class TimeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scheduleId;

    private Long userId;

    private LocalDateTime startTime;

    /** 结束时间，为 null 表示该片段仍在计时中 */
    private LocalDateTime endTime;

    /** 片段时长（秒） */
    private Integer duration;

    private LocalDateTime createdAt;
}
