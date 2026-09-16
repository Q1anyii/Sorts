package com.sorts.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 采纳 AI 规划请求（对应 api-spec.json 的 AdoptPlanRequest）。
 *
 * @author sorts
 */
@Data
public class AdoptPlanRequest {

    @NotBlank(message = "规划ID不能为空")
    private String planId;

    /** 选择采纳的建议下标；为空表示全部采纳 */
    private List<Integer> selectedIndices;

    private PlanAdjustments adjustments;
}
