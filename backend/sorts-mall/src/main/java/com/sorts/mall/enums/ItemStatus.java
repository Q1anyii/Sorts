package com.sorts.mall.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品状态（与 api-spec.json 的 MallItem.status 枚举一致）。
 *
 * @author sorts
 */
@Getter
@AllArgsConstructor
public enum ItemStatus {

    /** 在售 */
    ON_SALE("在售"),

    /** 已下架 */
    OFF_SHELF("已下架");

    private final String label;

    /** 宽松解析：未知状态一律按「已下架」处理，宁可少卖也不误卖 */
    public static ItemStatus from(String name) {
        if (name != null) {
            for (ItemStatus status : values()) {
                if (status.name().equalsIgnoreCase(name.trim())) {
                    return status;
                }
            }
        }
        return OFF_SHELF;
    }
}
