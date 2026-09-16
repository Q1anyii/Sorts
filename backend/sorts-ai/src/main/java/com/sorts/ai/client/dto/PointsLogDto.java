package com.sorts.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 光阴砂流水契约（对应 sorts-user 的 PointsLogVO）。
 *
 * @author sorts
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PointsLogDto {

    private Long id;

    private Integer changeAmount;

    private Integer balance;

    private String reason;

    private Long relatedId;

    private LocalDateTime createdAt;
}
