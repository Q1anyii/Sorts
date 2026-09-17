package com.sorts.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 主计划创建 / 更新请求。
 *
 * @author sorts
 */
@Data
public class ParentPlanSaveRequest {

    @NotBlank(message = "主计划标题不能为空")
    @Size(max = 200, message = "主计划标题长度不能超过 200 字")
    private String title;

    @Size(max = 2000, message = "描述长度不能超过 2000 字")
    private String description;

    /** 主题色（十六进制），缺省取默认色 */
    private String color;

    /** 优先级：LOW / MEDIUM / HIGH / URGENT，缺省 MEDIUM */
    private String priority;

    private LocalDate startDate;

    private LocalDate endDate;
}
