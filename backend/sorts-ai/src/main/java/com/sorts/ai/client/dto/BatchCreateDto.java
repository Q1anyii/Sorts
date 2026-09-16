package com.sorts.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量创建日程契约（对应 sorts-schedule 的 BatchCreateRequest）。
 *
 * @author sorts
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatchCreateDto {

    private List<ScheduleSaveDto> schedules;
}
