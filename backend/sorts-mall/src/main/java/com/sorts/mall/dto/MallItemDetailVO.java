package com.sorts.mall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 商品详情（在列表字段之上补充「本用户是否已拥有」与「总购买人次」）。
 *
 * @author sorts
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MallItemDetailVO extends MallItemVO {

    /** 当前用户是否已拥有 */
    private Boolean owned;

    /** 总购买人次 */
    private Long purchaseCount;

    public static MallItemDetailVO from(com.sorts.mall.entity.MallItem item, boolean owned, long purchaseCount) {
        MallItemDetailVO vo = new MallItemDetailVO();
        vo.setId(item.getId());
        vo.setName(item.getName());
        vo.setType(item.getType());
        vo.setDescription(item.getDescription());
        vo.setImageUrl(item.getImageUrl());
        vo.setPreviewUrl(item.getPreviewUrl());
        vo.setPrice(item.getPrice());
        vo.setStock(item.getStock());
        vo.setStatus(item.getStatus());
        vo.setCreatedAt(item.getCreatedAt());
        vo.setOwned(owned);
        vo.setPurchaseCount(purchaseCount);
        return vo;
    }
}
