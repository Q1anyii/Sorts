package com.sorts.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 规划覆盖的日期区间（含首尾）。
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanRange {

    private LocalDate startDate;

    private LocalDate endDate;
}
