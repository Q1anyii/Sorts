package com.sorts.mall.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品类型（与 api-spec.json 的 MallItem.type 枚举一致）。
 *
 * @author sorts
 */
@Getter
@AllArgsConstructor
public enum ItemType {

    /** 主题皮肤 */
    SKIN("主题皮肤"),

    /** 头像（含头像框） */
    AVATAR("头像装扮"),

    /** 徽章 */
    BADGE("徽章"),

    /** 贴纸 */
    STICKER("贴纸");

    private final String label;

    public static ItemType from(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (ItemType type : values()) {
            if (type.name().equalsIgnoreCase(name.trim())) {
                return type;
            }
        }
        return null;
    }

    /** 仅皮肤/头像/徽章参与「同类型只生效一个」，贴纸是可叠加的标记，不参与互斥 */
    public boolean isExclusiveActive() {
        return this == SKIN || this == AVATAR || this == BADGE;
    }
}
