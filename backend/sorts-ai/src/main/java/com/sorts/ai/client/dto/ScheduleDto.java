package com.sorts.ai.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日程契约对象（Feign 侧副本，对应 sorts-schedule 的 ScheduleVO）。
 *
 * <p>为什么不在 AI 服务里直接复用 sorts-schedule 的 VO：微服务之间应通过契约解耦，
 * 直接依赖对方的实体/VO 会让两个服务的发布节奏被绑死（对方改个字段名，AI 服务就编译不过）。
 * 这里只声明自己真正消费的字段，并用 ignoreUnknown 容忍对方扩展。</p>
 *
 * @author sorts
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScheduleDto {

    private Long id;

    private Long userId;

    private String title;

    private String description;

    private LocalDateTime plannedStartTime;

    /** 计划时长（分钟） */
    private Integer plannedDuration;

    private LocalDateTime actualStartTime;

    private LocalDateTime actualEndTime;

    /** 实际累计时长（秒） */
    private Integer actualDuration;

    private String status;

    private String priority;

    private List<String> tags;

    private String color;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
