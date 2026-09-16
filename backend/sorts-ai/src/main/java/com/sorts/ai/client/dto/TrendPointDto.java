package com.sorts.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDate;

/**
 * 按天趋势点（对应 sorts-schedule 的 TrendPointVO）。
 *
 * @author sorts
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrendPointDto {

    private LocalDate date;

    private Integer total;

    private Integer completed;

    /** 当日投入时长（秒） */
    private Integer totalDuration;

    private Double completionRate;
}
