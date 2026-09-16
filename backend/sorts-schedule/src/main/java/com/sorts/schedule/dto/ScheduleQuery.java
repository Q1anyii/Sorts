package com.sorts.schedule.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 日程列表查询条件（与 api-spec.json 的 GET /schedules 查询参数对齐）。
 *
 * @author sorts
 */
@Data
public class ScheduleQuery {

    /** 基准日期（配合 view 使用）：day / week / month 视图的锚点 */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** 视图粒度：day / week / month / all（缺省 all） */
    private String view;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /** 状态筛选：PENDING / IN_PROGRESS / PAUSED / COMPLETED / CANCELLED / TIMEOUT */
    private String status;

    /** 优先级筛选：LOW / MEDIUM / HIGH / URGENT */
    private String priority;

    /** 标签筛选 */
    private String tag;

    /** 关键词（标题 / 描述模糊匹配） */
    private String keyword;

    private Integer page = 1;

    private Integer pageSize = 20;

    /** 排序字段：plannedStartTime / createdAt / priority */
    private String sort = "plannedStartTime";

    /** 排序方向：asc / desc */
    private String order = "desc";
}
