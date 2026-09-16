package com.sorts.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日程创建 / 更新契约（对应 sorts-schedule 的 ScheduleSaveRequest）。
 *
 * @author sorts
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleSaveDto {

    private String title;

    private String description;

    private LocalDateTime plannedStartTime;

    /** 计划时长（分钟） */
    private Integer plannedDuration;

    /** LOW / MEDIUM / HIGH / URGENT */
    private String priority;

    private List<String> tags;

    private String color;
}
