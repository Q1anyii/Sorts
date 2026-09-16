package com.sorts.schedule.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日程创建 / 更新请求（字段与 api-spec.json 的 CreateScheduleRequest 对齐）。
 *
 * @author sorts
 */
@Data
public class ScheduleSaveRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题长度不能超过 128 字")
    private String title;

    @Size(max = 1024, message = "描述长度不能超过 1024 字")
    private String description;

    /** 计划开始时间，格式 yyyy-MM-dd HH:mm:ss */
    private LocalDateTime plannedStartTime;

    /** 计划时长（分钟） */
    @Min(value = 1, message = "计划时长至少 1 分钟")
    private Integer plannedDuration;

    /** 优先级：LOW / MEDIUM / HIGH / URGENT，缺省 MEDIUM */
    private String priority;

    /** 标签列表 */
    private List<String> tags;

    /** 主题色（十六进制） */
    private String color;
}
