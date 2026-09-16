package com.sorts.notification.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提醒候选日程（sorts-schedule 内部接口的出参副本）。
 *
 * <p>刻意不复用对方的 VO：跨服务只依赖 JSON 契约，对方重构字段不会把本服务编译搞崩。
 * {@code ignoreUnknown} 让上游新增字段时本服务照常工作。</p>
 *
 * @author sorts
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReminderCandidateDto {

    private Long userId;

    private Long scheduleId;

    private String title;

    private LocalDateTime plannedStartTime;

    private List<String> tags;
}
