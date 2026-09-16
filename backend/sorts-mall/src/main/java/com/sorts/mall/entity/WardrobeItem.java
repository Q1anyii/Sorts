package com.sorts.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 装扮仓库条目（对应 sorts_mall 库 t_wardrobe_item 表）。
 *
 * <p>{@code (user_id, item_id)} 上的唯一键是「同一装扮只能拥有一份」的最终防线：
 * 无论前端如何重试、锁是否失效，重复发放都会被数据库挡下。</p>
 *
 * @author sorts
 */
@Data
@TableName("t_wardrobe_item")
public class WardrobeItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long itemId;

    /** 商品类型冗余：切换装扮时按类型互斥，避免每次都要回查商品表 */
    private String itemType;

    /** 是否当前使用中：0 否 / 1 是 */
    private Integer isActive;

    private Long purchaseId;

    private Integer deleted;

    private LocalDateTime purchasedAt;
}
