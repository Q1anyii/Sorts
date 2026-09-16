package com.sorts.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水视图。
 *
 * @author sorts
 */
@Data
public class PointsLogVO {

    private Long id;

    private Integer changeAmount;

    private Integer balance;

    private String reason;

    private Long relatedId;

    private LocalDateTime createdAt;
}
