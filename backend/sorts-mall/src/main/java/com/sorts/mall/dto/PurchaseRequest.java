package com.sorts.mall.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 购买请求（与 api-spec.json 的 PurchaseRequest 一致）。
 *
 * @author sorts
 */
@Data
public class PurchaseRequest {

    @NotNull(message = "商品 ID 不能为空")
    private Long itemId;
}
