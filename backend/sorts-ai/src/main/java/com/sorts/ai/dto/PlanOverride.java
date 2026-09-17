package com.sorts.ai.dto;

import lombok.Data;

/**
 * 采纳时对单条建议的逐项覆盖（前端编辑后回传）。
 *
 * <p>index 必须指向原 suggestions 的下标（与 selectedIndices 同一坐标系），
 * 越界会被整体拒绝，防止静默改错条目。</p>
 *
 * @author sorts
 */
@Data
public class PlanOverride {

    private Integer index;

    private String title;

    /** 格式 HH:mm */
    private String suggestedStart;

    /** 分钟 */
    private Integer duration;
}
