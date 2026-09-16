package com.sorts.ai.client.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 日程列表查询条件（对应 sorts-schedule 的 ScheduleQuery，作为 GET 查询参数下发）。
 *
 * @author sorts
 */
@Data
public class ScheduleQueryDto {

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** day / week / month / all */
    private String view;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private String status;

    private String priority;

    private String tag;

    private String keyword;

    private Integer page = 1;

    private Integer pageSize = 20;

    private String sort = "plannedStartTime";

    private String order = "desc";
}
