package com.sorts.schedule.dto;

import com.sorts.schedule.entity.Schedule;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 日程视图对象（字段与 api-spec.json 的 Schedule 对齐）。
 *
 * @author sorts
 */
@Data
public class ScheduleVO {

    private Long id;

    private Long userId;

    /** 所属主计划ID（NULL=独立日程） */
    private Long parentId;

    private String title;

    private String description;

    private LocalDateTime plannedStartTime;

    /** 计划时长（分钟） */
    private Integer plannedDuration;

    private LocalDateTime actualStartTime;

    private LocalDateTime actualEndTime;

    /** 实际累计时长（秒，与 api-spec 一致） */
    private Integer actualDuration;

    private String status;

    private String priority;

    private List<String> tags;

    private String color;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static ScheduleVO from(Schedule schedule) {
        if (schedule == null) {
            return null;
        }
        ScheduleVO vo = new ScheduleVO();
        vo.setId(schedule.getId());
        vo.setUserId(schedule.getUserId());
        vo.setParentId(schedule.getParentId());
        vo.setTitle(schedule.getTitle());
        vo.setDescription(schedule.getDescription());
        vo.setPlannedStartTime(schedule.getPlannedStartTime());
        vo.setPlannedDuration(schedule.getPlannedDuration());
        vo.setActualStartTime(schedule.getActualStartTime());
        vo.setActualEndTime(schedule.getActualEndTime());
        vo.setActualDuration(schedule.getActualDuration());
        vo.setStatus(schedule.getStatus());
        vo.setPriority(schedule.getPriority());
        vo.setTags(splitTags(schedule.getTags()));
        vo.setColor(schedule.getColor());
        vo.setCreatedAt(schedule.getCreatedAt());
        vo.setUpdatedAt(schedule.getUpdatedAt());
        return vo;
    }

    /** 数据库以逗号分隔存储标签，出参转为数组 */
    public static List<String> splitTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    /** 入参标签数组转为逗号分隔存储值 */
    public static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse(null);
    }
}
