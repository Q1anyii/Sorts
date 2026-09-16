package com.sorts.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 购买结果（与 api-spec.json 的 /mall/purchase 出参一致）。
 *
 * @author sorts
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseResultVO {

    /** 购买记录 ID（前端可用于「查看订单」） */
    private Long purchaseId;

    private MallItemVO item;

    /** 扣减后的光阴砂余额 */
    private Integer remainingPoints;
}
