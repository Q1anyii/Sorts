package com.sorts.mall.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 内部积分变动指令（与 sorts-user 的 /users/points/change 契约对应）。
 *
 * <p>刻意不在服务间共享实体类：双方只依赖 JSON 契约，避免发布节奏被绑死。</p>
 *
 * @author sorts
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointsChangeCommand {

    /** 变动值，正增负减 */
    private Integer delta;

    private String reason;

    private Long relatedId;
}
