package com.sorts.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 光阴砂账户契约（对应 sorts-user 的 PointsVO）。
 *
 * @author sorts
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PointsDto {

    private Integer points;

    private List<PointsLogDto> logs;
}
