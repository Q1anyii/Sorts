package com.sorts.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.common.result.PageData;
import com.sorts.schedule.dto.ReminderCandidateVO;
import com.sorts.schedule.dto.ScheduleQuery;
import com.sorts.schedule.dto.ScheduleSaveRequest;
import com.sorts.schedule.dto.ScheduleVO;
import com.sorts.schedule.entity.Schedule;
import com.sorts.schedule.enums.SchedulePriority;
import com.sorts.schedule.enums.ScheduleStatus;
import com.sorts.schedule.mapper.ScheduleMapper;
import com.sorts.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 日程基础服务实现。
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    /** 允许排序的字段白名单，防止 SQL 注入 */
    private static final Map<String, String> SORTABLE_COLUMNS = Map.of(
            "plannedStartTime", "planned_start_time",
            "createdAt", "created_at",
            "updatedAt", "updated_at",
            "actualDuration", "actual_duration"
    );

    /**
     * 优先级的排序表达式：字段值本身是字符串，字典序无意义，
     * 因此用 FIELD() 映射成「低 → 高」的序号再排序（常量字符串，无注入风险）。
     */
    private static final String PRIORITY_ORDER_EXPRESSION = "FIELD(priority, 'LOW', 'MEDIUM', 'HIGH', 'URGENT')";

    private static final Set<String> VALID_VIEWS = Set.of("day", "week", "month", "all");

    private static final int MAX_PAGE_SIZE = 200;

    /** 提醒扫描单次最多返回的候选日程数，防止一次扫描把内存与下游压垮 */
    private static final int MAX_REMINDER_CANDIDATES = 500;

    private final ScheduleMapper scheduleMapper;

    @Override
    public ScheduleVO create(Long userId, ScheduleSaveRequest request) {
        Schedule schedule = buildEntity(userId, request);
        scheduleMapper.insert(schedule);
        log.info("创建日程 userId={}, scheduleId={}, title={}", userId, schedule.getId(), schedule.getTitle());
        return ScheduleVO.from(schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ScheduleVO> batchCreate(Long userId, List<ScheduleSaveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "日程列表不能为空");
        }
        List<ScheduleVO> created = new ArrayList<>(requests.size());
        for (ScheduleSaveRequest request : requests) {
            Schedule schedule = buildEntity(userId, request);
            scheduleMapper.insert(schedule);
            created.add(ScheduleVO.from(schedule));
        }
        log.info("批量创建日程 userId={}, count={}", userId, created.size());
        return created;
    }

    @Override
    public ScheduleVO update(Long userId, Long scheduleId, ScheduleSaveRequest request) {
        Schedule schedule = requireOwned(userId, scheduleId);
        if (ScheduleStatus.valueOf(schedule.getStatus()).isFinal()) {
            throw new BizException(ErrorCode.CONFLICT, "已结束的日程不可修改");
        }
        if (StringUtils.hasText(request.getTitle())) {
            schedule.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            schedule.setDescription(request.getDescription());
        }
        if (request.getPlannedStartTime() != null) {
            schedule.setPlannedStartTime(request.getPlannedStartTime());
        }
        if (request.getPlannedDuration() != null) {
            schedule.setPlannedDuration(request.getPlannedDuration());
        }
        if (StringUtils.hasText(request.getPriority())) {
            schedule.setPriority(validatePriority(request.getPriority()));
        }
        if (request.getTags() != null) {
            schedule.setTags(ScheduleVO.joinTags(request.getTags()));
        }
        if (StringUtils.hasText(request.getColor())) {
            schedule.setColor(request.getColor());
        }
        scheduleMapper.updateById(schedule);
        return ScheduleVO.from(schedule);
    }

    @Override
    public void delete(Long userId, Long scheduleId) {
        // 自定义 UPDATE 一次完成：属主校验 + deleted=1 + 审计时间，避免两步操作留中间态
        int affected = scheduleMapper.softDeleteOwned(userId, scheduleId);
        if (affected != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "日程不存在");
        }
        log.info("删除日程 userId={}, scheduleId={}", userId, scheduleId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBatch(Long userId, List<Long> scheduleIds) {
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择要删除的日程");
        }
        List<Long> distinct = scheduleIds.stream().distinct().toList();
        if (distinct.size() > 200) {
            throw new BizException(ErrorCode.PARAM_ERROR, "单次最多删除 200 条日程");
        }
        int affected = scheduleMapper.softDeleteBatchOwned(userId, distinct);
        // 影响行数 < 请求数说明存在不属于该用户或已删除的记录：整体回滚，全部成功或全部失败
        if (affected != distinct.size()) {
            throw new BizException(ErrorCode.NOT_FOUND, "部分日程不存在或无权删除，已整体回滚");
        }
        log.info("批量删除日程 userId={}, count={}", userId, distinct.size());
    }

    @Override
    public ScheduleVO get(Long userId, Long scheduleId) {
        return ScheduleVO.from(requireOwned(userId, scheduleId));
    }

    @Override
    public PageData<ScheduleVO> list(Long userId, ScheduleQuery query) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getUserId, userId);

        applyTimeRange(wrapper, query);
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(Schedule::getStatus, query.getStatus().trim().toUpperCase());
        }
        if (StringUtils.hasText(query.getPriority())) {
            wrapper.eq(Schedule::getPriority, query.getPriority().trim().toUpperCase());
        }
        if (StringUtils.hasText(query.getTag())) {
            // tags 以逗号分隔存储，FIND_IN_SET 可精确匹配单个标签（参数绑定，无注入风险）
            wrapper.apply("FIND_IN_SET({0}, tags)", query.getTag().trim());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Schedule::getTitle, keyword).or().like(Schedule::getDescription, keyword));
        }

        applySort(wrapper, query);

        int page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1
                ? 20
                : Math.min(query.getPageSize(), MAX_PAGE_SIZE);

        Page<Schedule> result = scheduleMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<ScheduleVO> list = result.getRecords().stream().map(ScheduleVO::from).toList();
        return PageData.of(list, result.getTotal(), page, pageSize);
    }

    @Override
    public Schedule requireOwned(Long userId, Long scheduleId) {
        // 已逻辑删除的数据由 MyBatis-Plus 自动过滤，这里只区分「不存在」与「越权」
        Schedule schedule = scheduleId == null ? null : scheduleMapper.selectById(scheduleId);
        // 越权访问一律按不存在处理，避免暴露他人数据的存在性
        if (schedule == null || !schedule.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "日程不存在");
        }
        return schedule;
    }

    @Override
    public List<Schedule> listInRange(Long userId, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getUserId, userId);
        // 边界允许为空，便于「全部」口径复用同一查询
        if (start != null) {
            wrapper.ge(Schedule::getPlannedStartTime, start.atStartOfDay());
        }
        if (end != null) {
            wrapper.lt(Schedule::getPlannedStartTime, end.plusDays(1).atStartOfDay());
        }
        wrapper.orderByAsc(Schedule::getPlannedStartTime).orderByAsc(Schedule::getId);
        return scheduleMapper.selectList(wrapper);
    }

    @Override
    public List<Schedule> listUpcoming(Long userId, int limit) {
        int size = limit < 1 ? 5 : Math.min(limit, MAX_PAGE_SIZE);
        LocalDate today = LocalDate.now();
        return scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getUserId, userId)
                .eq(Schedule::getStatus, ScheduleStatus.PENDING.name())
                .ge(Schedule::getPlannedStartTime, today.atStartOfDay())
                .orderByAsc(Schedule::getPlannedStartTime)
                .last("LIMIT " + size));
    }

    @Override
    public List<ReminderCandidateVO> listReminderCandidates(LocalDateTime from, LocalDateTime to, int limit) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "提醒扫描时间窗口非法");
        }
        int size = limit < 1 ? MAX_REMINDER_CANDIDATES : Math.min(limit, MAX_REMINDER_CANDIDATES);
        List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getStatus, ScheduleStatus.PENDING.name())
                .ge(Schedule::getPlannedStartTime, from)
                .le(Schedule::getPlannedStartTime, to)
                .orderByAsc(Schedule::getPlannedStartTime)
                .last("LIMIT " + size));
        return schedules.stream().map(ReminderCandidateVO::from).toList();
    }

    private Schedule buildEntity(Long userId, ScheduleSaveRequest request) {
        Schedule schedule = new Schedule();
        schedule.setUserId(userId);
        schedule.setTitle(request.getTitle());
        schedule.setDescription(request.getDescription());
        schedule.setPlannedStartTime(request.getPlannedStartTime());
        schedule.setPlannedDuration(request.getPlannedDuration());
        schedule.setPriority(validatePriority(request.getPriority()));
        schedule.setTags(ScheduleVO.joinTags(request.getTags()));
        schedule.setColor(StringUtils.hasText(request.getColor()) ? request.getColor() : null);
        schedule.setStatus(ScheduleStatus.PENDING.name());
        schedule.setActualDuration(0);
        schedule.setDeleted(0);
        return schedule;
    }

    /** 优先级缺省 MEDIUM，取值非法直接拒绝 */
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

    /** 时间范围：显式 startDate/endDate 优先，其次按 view + date（day/week/month）推导 */
    private void applyTimeRange(LambdaQueryWrapper<Schedule> wrapper, ScheduleQuery query) {
        LocalDate start = query.getStartDate();
        LocalDate end = query.getEndDate();

        String view = StringUtils.hasText(query.getView()) ? query.getView().trim().toLowerCase() : "all";
        if (!VALID_VIEWS.contains(view)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "view 取值非法：" + query.getView());
        }

        if (start == null && end == null && !"all".equals(view)) {
            LocalDate anchor = query.getDate() == null ? LocalDate.now() : query.getDate();
            switch (view) {
                case "day" -> {
                    start = anchor;
                    end = anchor;
                }
                case "week" -> {
                    start = anchor.with(DayOfWeek.MONDAY);
                    end = start.plusDays(6);
                }
                case "month" -> {
                    start = anchor.withDayOfMonth(1);
                    end = start.withDayOfMonth(start.lengthOfMonth());
                }
                default -> {
                    // all：不加时间条件
                }
            }
        } else if (query.getDate() != null && start == null && end == null) {
            start = query.getDate();
            end = query.getDate();
        }

        if (start != null) {
            wrapper.ge(Schedule::getPlannedStartTime, start.atStartOfDay());
        }
        if (end != null) {
            wrapper.lt(Schedule::getPlannedStartTime, end.plusDays(1).atStartOfDay());
        }
    }

    private void applySort(LambdaQueryWrapper<Schedule> wrapper, ScheduleQuery query) {
        wrapper.last(buildOrderBy(query.getSort(), query.getOrder()));
    }

    /**
     * 生成 ORDER BY 片段（包级可见，便于单测直接断言排序安全性）。
     *
     * <p>两条防线：① 排序字段只允许白名单内的映射结果，其余一律回落默认字段；
     * ② 恒定追加 {@code id DESC} 兜底，保证同一排序值下分页结果稳定。</p>
     */
    String buildOrderBy(String sort, String order) {
        boolean asc = "asc".equalsIgnoreCase(order);
        String field = sort == null ? "" : sort.trim();
        // 优先级按业务语义排序（URGENT 最高），而非字符串字典序
        String expression = "priority".equals(field)
                ? PRIORITY_ORDER_EXPRESSION
                : SORTABLE_COLUMNS.getOrDefault(field, SORTABLE_COLUMNS.get("plannedStartTime"));
        return "ORDER BY " + expression + (asc ? " ASC" : " DESC") + ", id DESC";
    }
}
