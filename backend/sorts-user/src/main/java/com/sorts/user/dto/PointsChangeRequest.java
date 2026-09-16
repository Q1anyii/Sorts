package com.sorts.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 积分变动请求（供内部服务通过 Feign 调用）。
 *
 * @author sorts
 */
@Data
public class PointsChangeRequest {

    /** 变动值，正增负减 */
    @NotNull(message = "delta 不能为空")
    private Integer delta;

    /** 变动原因，例如「落梭奖励」「锦市消费」 */
    private String reason;

    /** 关联业务 ID */
    private Long relatedId;
}
