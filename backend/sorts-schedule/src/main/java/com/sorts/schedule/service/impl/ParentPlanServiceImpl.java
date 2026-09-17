package com.sorts.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.schedule.dto.ParentPlanSaveRequest;
import com.sorts.schedule.dto.ParentPlanVO;
import com.sorts.schedule.dto.ScheduleVO;
import com.sorts.schedule.entity.ParentPlan;
import com.sorts.schedule.entity.Schedule;
import com.sorts.schedule.enums.SchedulePriority;
import com.sorts.schedule.mapper.ParentPlanMapper;
import com.sorts.schedule.mapper.ScheduleMapper;
import com.sorts.schedule.service.ParentPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主计划（长时间计划容器）服务实现。
 *
 * <p>状态机：PENDING → IN_PROGRESS ⇄ PAUSED → COMPLETED。
 * 长时间计划不做分钟级穿梭结算，进度由子计划完成情况聚合得出。</p>
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParentPlanServiceImpl implements ParentPlanService {

    private static final Map<String, String> STATUS_TRANSITIONS = Map.of(
            "start", "PENDING|PAUSED",
            "pause", "IN_PROGRESS",
            "resume", "PAUSED",
            "complete", "IN_PROGRESS|PAUSED"
    );

    private final ParentPlanMapper parentPlanMapper;
    private final ScheduleMapper scheduleMapper;

    @Override
    public ParentPlanVO create(Long userId, ParentPlanSaveRequest req) {
        ParentPlan plan = new ParentPlan();
        plan.setUserId(userId);
        plan.setTitle(req.getTitle().trim());
        plan.setDescription(req.getDescription());
        plan.setColor(StringUtils.hasText(req.getColor()) ? req.getColor() : null);
        plan.setPriority(validatePriority(req.getPriority()));
        plan.setStartDate(req.getStartDate());
        plan.setEndDate(req.getEndDate());
        plan.setStatus("PENDING");
        plan.setDeleted(0);
        parentPlanMapper.insert(plan);
        log.info("创建主计划 userId={}, parentPlanId={}, title={}", userId, plan.getId(), plan.getTitle());
        return ParentPlanVO.from(plan, 0, 0, List.of());
    }

    @Override
    public List<ParentPlanVO> page(Long userId, int page, int pageSize) {
        Page<ParentPlan> p = parentPlanMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<ParentPlan>()
                        .eq(ParentPlan::getUserId, userId)
                        .eq(ParentPlan::getDeleted, 0)
                        .orderByDesc(ParentPlan::getCreatedAt));
        List<ParentPlan> records = p.getRecords();
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, long[]> agg = aggregate(records);
        List<ParentPlanVO> vos = new ArrayList<>(records.size());
        for (ParentPlan plan : records) {
            long[] stats = agg.getOrDefault(plan.getId(), new long[]{0, 0});
            vos.add(ParentPlanVO.from(plan, (int) stats[0], (int) stats[1], List.of()));
        }
        return vos;
    }

    @Override
    public long count(Long userId) {
        return parentPlanMapper.selectCount(new LambdaQueryWrapper<ParentPlan>()
                .eq(ParentPlan::getUserId, userId)
                .eq(ParentPlan::getDeleted, 0));
    }

    @Override
    public ParentPlanVO detail(Long userId, Long id) {
        ParentPlan plan = requireOwned(userId, id);
        List<Schedule> children = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getParentId, id)
                .eq(Schedule::getUserId, userId)
                .eq(Schedule::getDeleted, 0)
                .orderByAsc(Schedule::getPlannedStartTime));
        int done = 0;
        List<ScheduleVO> childVos = new ArrayList<>(children.size());
        for (Schedule s : children) {
            if ("COMPLETED".equals(s.getStatus())) {
                done++;
            }
            childVos.add(ScheduleVO.from(s));
        }
        return ParentPlanVO.from(plan, children.size(), done, childVos);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParentPlanVO update(Long userId, Long id, ParentPlanSaveRequest req) {
        ParentPlan plan = requireOwned(userId, id);
        if (StringUtils.hasText(req.getTitle())) {
            plan.setTitle(req.getTitle().trim());
        }
        plan.setDescription(req.getDescription());
        plan.setColor(StringUtils.hasText(req.getColor()) ? req.getColor() : null);
        plan.setPriority(validatePriority(req.getPriority()));
        plan.setStartDate(req.getStartDate());
        plan.setEndDate(req.getEndDate());
        parentPlanMapper.updateById(plan);
        log.info("更新主计划 userId={}, parentPlanId={}", userId, id);
        return detail(userId, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        int rows = parentPlanMapper.softDeleteOwned(userId, id);
        if (rows == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "主计划不存在或无权删除");
        }
        // 解除全部子计划归属：子计划保留为独立日程，不级联删除
        parentPlanMapper.clearChildren(userId, id);
        log.info("删除主计划 userId={}, parentPlanId={}（子计划已解除挂载）", userId, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParentPlanVO changeStatus(Long userId, Long id, String action) {
        ParentPlan plan = requireOwned(userId, id);
        String actionKey = action == null ? "" : action.trim().toLowerCase();
        String allowed = STATUS_TRANSITIONS.get(actionKey);
        if (allowed == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的状态动作：" + action);
        }
        if (!allowed.contains(plan.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT,
                    "主计划当前状态不允许执行「" + actionKey + "」：" + plan.getStatus());
        }
        switch (actionKey) {
            case "start" -> {
                if ("PENDING".equals(plan.getStatus())) {
                    plan.setStartedAt(LocalDateTime.now());
                }
                plan.setStatus("IN_PROGRESS");
            }
            case "pause" -> plan.setStatus("PAUSED");
            case "resume" -> plan.setStatus("IN_PROGRESS");
            case "complete" -> plan.setStatus("COMPLETED");
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "不支持的状态动作：" + action);
        }
        parentPlanMapper.updateById(plan);
        log.info("主计划状态流转 userId={}, parentPlanId={}, action={}, status={}", userId, id, actionKey, plan.getStatus());
        return detail(userId, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParentPlanVO attachChildren(Long userId, Long id, List<Long> scheduleIds) {
        requireOwned(userId, id);
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "子计划列表不能为空");
        }
        List<Schedule> owned = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .in(Schedule::getId, scheduleIds)
                .eq(Schedule::getUserId, userId)
                .eq(Schedule::getDeleted, 0));
        if (owned.size() != scheduleIds.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "部分日程不存在或无权操作");
        }
        int updated = parentPlanMapper.attachChildren(userId, id, scheduleIds);
        log.info("挂载子计划 userId={}, parentPlanId={}, count={}, updated={}", userId, id, scheduleIds.size(), updated);
        return detail(userId, id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParentPlanVO detachChild(Long userId, Long id, Long scheduleId) {
        requireOwned(userId, id);
        int rows = parentPlanMapper.detachChild(userId, id, scheduleId);
        if (rows == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "子计划不存在或不在该主计划下");
        }
        log.info("移出子计划 userId={}, parentPlanId={}, scheduleId={}", userId, id, scheduleId);
        return detail(userId, id);
    }

    private ParentPlan requireOwned(Long userId, Long id) {
        ParentPlan plan = parentPlanMapper.selectOne(new LambdaQueryWrapper<ParentPlan>()
                .eq(ParentPlan::getId, id)
                .eq(ParentPlan::getUserId, userId)
                .eq(ParentPlan::getDeleted, 0));
        if (plan == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "主计划不存在");
        }
        return plan;
    }

    private Map<Long, long[]> aggregate(List<ParentPlan> records) {
        List<Long> ids = records.stream().map(ParentPlan::getId).toList();
        List<Map<String, Object>> rows = parentPlanMapper.aggregateChildren(ids);
        Map<Long, long[]> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Number parentId = (Number) row.get("parentId");
            Number total = (Number) row.get("total");
            Number done = (Number) row.get("done");
            map.put(parentId.longValue(), new long[]{total == null ? 0 : total.longValue(), done == null ? 0 : done.longValue()});
        }
        return map;
    }

    private String validatePriority(String priority) {
        if (!StringUtils.hasText(priority)) {
            return SchedulePriority.MEDIUM.name();
        }
        try {
            return SchedulePriority.valueOf(priority.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "优先级取值非法：" + priority);
        }
    }
}
