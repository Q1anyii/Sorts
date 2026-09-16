package com.sorts.ai.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 采纳规划时的手动调整（对应 api-spec.json 的 AdoptPlanRequest.adjustments）。
 *
 * @author sorts
 */
@Data
public class PlanAdjustments {

    /** 整体开始时间偏移（分钟），可正可负 */
    private Integer startOffset;

    /** 覆盖规划的目标日期 */
    private LocalDate date;
}
