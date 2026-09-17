package com.sorts.schedule.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量挂载子计划请求。
 *
 * @author sorts
 */
@Data
public class AttachChildrenRequest {

    /** 要挂载到主计划下的日程 ID 列表（须属于当前用户） */
    @NotEmpty(message = "子计划列表不能为空")
    private List<Long> scheduleIds;
}
