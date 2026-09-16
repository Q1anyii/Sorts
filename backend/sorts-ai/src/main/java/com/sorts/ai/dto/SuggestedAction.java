package com.sorts.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 建议操作（对应 api-spec.json 的 suggestedActions 项）。
 *
 * <p>由「本次对话实际做了什么」推导，而不是让模型凭空生成：
 * 模型编造的操作按钮点下去会失败，这种「看起来能用其实不能用」的按钮
 * 对信任的伤害比没有按钮更大。</p>
 *
 * @author sorts
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedAction {

    /** CREATE_SCHEDULE / VIEW_STATS / GENERATE_SUMMARY */
    private String type;

    private String label;

    /** 前端据此跳转或预填，如 {"scheduleIds":[1,2]} */
    private Map<String, Object> payload;
}
