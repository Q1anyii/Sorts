package com.sorts.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 虚拟商品实体（对应 sorts_mall 库 t_mall_item 表）。
 *
 * @author sorts
 */
@Data
@TableName("t_mall_item")
public class MallItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 见 ItemType */
    private String type;

    private String description;

    private String imageUrl;

    private String previewUrl;

    /** 所需光阴砂 */
    private Integer price;

    /** 库存，-1 表示无限 */
    private Integer stock;

    /** 见 ItemStatus */
    private String status;

    private Integer sortOrder;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 是否无限库存 */
    public boolean unlimited() {
        return stock != null && stock < 0;
    }
}
