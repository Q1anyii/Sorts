package com.sorts.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量创建日程请求（AI 规划一键采纳、导入等场景）。
 *
 * @author sorts
 */
@Data
public class BatchCreateRequest {

    @NotEmpty(message = "日程列表不能为空")
    @Valid
    private List<ScheduleSaveRequest> schedules;
}
