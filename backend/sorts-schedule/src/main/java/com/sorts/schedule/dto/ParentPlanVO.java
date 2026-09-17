package com.sorts.schedule.dto;

import com.sorts.schedule.entity.ParentPlan;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 主计划视图对象：含子计划聚合统计（进度）与可选子计划明细。
 *
 * @author sorts
 */
@Data
public class ParentPlanVO {

    private Long id;

    private Long userId;

    private String title;

    private String description;

    private String color;

    private String priority;

    private LocalDate startDate;

    private LocalDate endDate;

    private String status;

    private LocalDateTime startedAt;

    /** 已进行天数（IN_PROGRESS / PAUSED 时有效，按 started_at 计算） */
    private Long daysRunning;

    /** 子计划总数 */
    private Integer childCount;

    /** 已完成的子计划数 */
    private Integer completedCount;

    /** 完成度 0-100（子计划为空时按 0 计） */
    private Integer progress;

    /** 子计划明细（仅详情接口返回） */
    private List<ScheduleVO> children;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static ParentPlanVO from(ParentPlan p, Integer childCount, Integer completedCount, List<ScheduleVO> children) {
        if (p == null) {
            return null;
        }
        ParentPlanVO vo = new ParentPlanVO();
        vo.setId(p.getId());
        vo.setUserId(p.getUserId());
        vo.setTitle(p.getTitle());
        vo.setDescription(p.getDescription());
        vo.setColor(p.getColor());
        vo.setPriority(p.getPriority());
        vo.setStartDate(p.getStartDate());
        vo.setEndDate(p.getEndDate());
        vo.setStatus(p.getStatus());
        vo.setStartedAt(p.getStartedAt());
        if (p.getStartedAt() != null && !"PENDING".equals(p.getStatus()) && !"COMPLETED".equals(p.getStatus())) {
            vo.setDaysRunning(Math.max(1, ChronoUnit.DAYS.between(p.getStartedAt().toLocalDate(), LocalDate.now()) + 1));
        }
        int total = childCount == null ? 0 : childCount;
        int done = completedCount == null ? 0 : completedCount;
        vo.setChildCount(total);
        vo.setCompletedCount(done);
        vo.setProgress(total == 0 ? 0 : (int) Math.round(done * 100.0 / total));
        vo.setChildren(children == null ? new ArrayList<>() : children);
        vo.setCreatedAt(p.getCreatedAt());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }
}
