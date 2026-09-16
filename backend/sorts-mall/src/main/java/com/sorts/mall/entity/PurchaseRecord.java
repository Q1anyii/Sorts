package com.sorts.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购买记录实体（对应 sorts_mall 库 t_purchase_record 表）。
 *
 * <p>商品名与成交价都是<b>快照</b>：商品后续改名、调价或下架，
 * 都不能让历史订单跟着变——账目类数据一旦「可被后来者修改」就失去了凭证意义。</p>
 *
 * @author sorts
 */
@Data
@TableName("t_purchase_record")
public class PurchaseRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long itemId;

    /** 商品名快照 */
    private String itemName;

    /** 成交价快照 */
    private Integer price;

    /** 成交后余额快照 */
    private Integer pointsAfter;

    private LocalDateTime createdAt;
}
