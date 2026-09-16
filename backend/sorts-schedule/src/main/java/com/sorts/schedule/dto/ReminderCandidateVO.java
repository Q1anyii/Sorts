package com.sorts.schedule.dto;

import com.sorts.schedule.entity.Schedule;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提醒候选日程（内部接口出参，供通知服务扫描定时提醒使用）。
 *
 * <p>只带提醒文案需要的最小字段：跨服务传输的字段越少，越不容易在演进口径时踩到对方。</p>
 *
 * @author sorts
 */
@Data
public class ReminderCandidateVO {

    private Long userId;

    private Long scheduleId;

    private String title;

    /** 计划开始时间 */
    private LocalDateTime plannedStartTime;

    private List<String> tags;

    public static ReminderCandidateVO from(Schedule schedule) {
        ReminderCandidateVO vo = new ReminderCandidateVO();
        vo.setUserId(schedule.getUserId());
        vo.setScheduleId(schedule.getId());
        vo.setTitle(schedule.getTitle());
        vo.setPlannedStartTime(schedule.getPlannedStartTime());
        vo.setTags(ScheduleVO.splitTags(schedule.getTags()));
        return vo;
    }
}
