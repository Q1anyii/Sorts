package com.sorts.mall.dto;

import com.sorts.mall.entity.MallItem;
import com.sorts.mall.entity.WardrobeItem;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 装扮仓库条目（字段与 api-spec.json 的 WardrobeItem 一致）。
 *
 * @author sorts
 */
@Data
public class WardrobeItemVO {

    private Long id;

    private Long userId;

    /** 商品明细 */
    private MallItemVO item;

    /** 是否当前使用中 */
    private Boolean isActive;

    private LocalDateTime purchasedAt;

    public static WardrobeItemVO from(WardrobeItem wardrobe, MallItem item) {
        WardrobeItemVO vo = new WardrobeItemVO();
        vo.setId(wardrobe.getId());
        vo.setUserId(wardrobe.getUserId());
        vo.setItem(MallItemVO.from(item));
        vo.setIsActive(Integer.valueOf(1).equals(wardrobe.getIsActive()));
        vo.setPurchasedAt(wardrobe.getPurchasedAt());
        return vo;
    }
}
