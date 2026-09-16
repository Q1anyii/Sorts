package com.sorts.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分（光阴砂）变动流水。
 *
 * @author sorts
 */
@Data
@TableName("t_points_log")
public class PointsLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 变动值，正数增加 / 负数扣减 */
    private Integer changeAmount;

    /** 变动后的余额快照，便于对账 */
    private Integer balance;

    /** 变动原因说明 */
    private String reason;

    /** 关联业务 ID（日程 / 商品等） */
    private Long relatedId;

    private LocalDateTime createdAt;
}
