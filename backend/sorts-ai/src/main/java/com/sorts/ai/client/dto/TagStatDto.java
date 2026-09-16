package com.sorts.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 标签维度统计（对应 sorts-schedule 的 TagStatVO）。
 *
 * @author sorts
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TagStatDto {

    private String tag;

    private Integer count;

    /** 该标签实际投入时长（秒） */
    private Integer totalDuration;

    private Integer completedCount;

    /** 占比 0-1 */
    private Double percentage;
}
