package com.sorts.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.schedule.dto.ScheduleVO;
import com.sorts.schedule.entity.Schedule;
import com.sorts.schedule.entity.TimeRecord;
import com.sorts.schedule.enums.ScheduleStatus;
import com.sorts.schedule.mapper.ScheduleMapper;
import com.sorts.schedule.mapper.TimeRecordMapper;
import com.sorts.schedule.repository.ActiveTimerStore;
import com.sorts.schedule.service.PointsRewardService;
import com.sorts.schedule.service.ScheduleService;
import com.sorts.schedule.service.TimerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 计时状态机实现。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li><b>分段计时</b>：每次 start / resume 开启一个 {@code t_time_record} 片段，
 *       pause / end / cancel 结算该片段。暂停与恢复天然幂等，且可回溯每段时间。</li>
 *   <li><b>总时长重算而非累加</b>：每次都依据全部片段的秒数和重新计算分钟数，
 *       避免多次四舍五入造成的累计误差。</li>
 *   <li><b>Redis 兜底</b>：若片段开始时间只存在于 Redis（异常重启导致 DB 未落库），
 *       结算时补写一条片段，保证已投入时间不丢。</li>
 * </ul>
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimerServiceImpl implements TimerService {

    private final ScheduleService scheduleService;

    private final ScheduleMapper scheduleMapper;

    private final TimeRecordMapper timeRecordMapper;

    private final ActiveTimerStore activeTimerStore;

    private final PointsRewardService pointsRewardService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO start(Long userId, Long scheduleId) {
        Schedule schedule = scheduleService.requireOwned(userId, scheduleId);
        ScheduleStatus status = currentStatus(schedule);
        if (!ScheduleStatus.startableFrom().contains(status)) {
            throw new BizException(ErrorCode.CONFLICT, "当前状态（" + status.getLabel() + "）不可开始计时");
        }
        assertNoOtherRunning(userId, scheduleId);

        LocalDateTime now = LocalDateTime.now();
        if (schedule.getActualStartTime() == null) {
            schedule.setActualStartTime(now);
        }
        schedule.setStatus(ScheduleStatus.IN_PROGRESS.name());
        scheduleMapper.updateById(schedule);
        openSegment(userId, scheduleId, now);
        return ScheduleVO.from(schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO pause(Long userId, Long scheduleId) {
        Schedule schedule = scheduleService.requireOwned(userId, scheduleId);
        ScheduleStatus status = currentStatus(schedule);
        if (!ScheduleStatus.pausableFrom().contains(status)) {
            throw new BizException(ErrorCode.CONFLICT, "仅穿梭中的日程可暂停");
        }
        settleSegment(userId, schedule, LocalDateTime.now());
        schedule.setStatus(ScheduleStatus.PAUSED.name());
        scheduleMapper.updateById(schedule);
        return ScheduleVO.from(schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO resume(Long userId, Long scheduleId) {
        Schedule schedule = scheduleService.requireOwned(userId, scheduleId);
        ScheduleStatus status = currentStatus(schedule);
        if (status != ScheduleStatus.PAUSED) {
            throw new BizException(ErrorCode.CONFLICT, "仅已暂停的日程可续梭");
        }
        assertNoOtherRunning(userId, scheduleId);

        LocalDateTime now = LocalDateTime.now();
        schedule.setStatus(ScheduleStatus.IN_PROGRESS.name());
        scheduleMapper.updateById(schedule);
        openSegment(userId, scheduleId, now);
        return ScheduleVO.from(schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO end(Long userId, Long scheduleId) {
        Schedule schedule = scheduleService.requireOwned(userId, scheduleId);
        ScheduleStatus status = currentStatus(schedule);
        if (!ScheduleStatus.endableFrom().contains(status)) {
            throw new BizException(ErrorCode.CONFLICT, "当前状态（" + status.getLabel() + "）不可结束");
        }
        LocalDateTime now = LocalDateTime.now();
        if (status == ScheduleStatus.IN_PROGRESS) {
            settleSegment(userId, schedule, now);
        }
        schedule.setActualEndTime(now);
        schedule.setStatus(ScheduleStatus.COMPLETED.name());
        scheduleMapper.updateById(schedule);

        // 落梭奖励（失败仅告警，不回滚主流程）
        pointsRewardService.rewardOnComplete(userId, scheduleId);
        return ScheduleVO.from(schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleVO cancel(Long userId, Long scheduleId) {
        Schedule schedule = scheduleService.requireOwned(userId, scheduleId);
        ScheduleStatus status = currentStatus(schedule);
        if (!ScheduleStatus.cancellableFrom().contains(status)) {
            throw new BizException(ErrorCode.CONFLICT, "当前状态（" + status.getLabel() + "）不可取消");
        }
        // 取消时保留已投入的时间痕迹
        if (status == ScheduleStatus.IN_PROGRESS) {
            settleSegment(userId, schedule, LocalDateTime.now());
        }
        activeTimerStore.clearSegment(userId, scheduleId);
        schedule.setStatus(ScheduleStatus.CANCELLED.name());
        scheduleMapper.updateById(schedule);
        return ScheduleVO.from(schedule);
    }

    @Override
    public ScheduleVO getActive(Long userId) {
        Schedule active = scheduleMapper.selectOne(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getUserId, userId)
                .eq(Schedule::getStatus, ScheduleStatus.IN_PROGRESS.name())
                .orderByDesc(Schedule::getId)
                .last("LIMIT 1"));
        return active == null ? null : ScheduleVO.from(active);
    }

    /** 开启一个计时片段，并在 Redis 标记活跃计时 */
    private void openSegment(Long userId, Long scheduleId, LocalDateTime start) {
        TimeRecord record = new TimeRecord();
        record.setScheduleId(scheduleId);
        record.setUserId(userId);
        record.setStartTime(start);
        record.setDuration(0);
        timeRecordMapper.insert(record);
        activeTimerStore.startSegment(userId, scheduleId, toEpochMilli(start));
    }

    /**
     * 结算当前片段：写入结束时间与秒数，清理 Redis 活跃标记，并重算总时长。
     *
     * <p>片段开始时间以 <b>DB 未闭合片段为准</b>（权威来源，服务重启也不会算错）；
     * 仅当 DB 查不到片段时才回退到 Redis 补记，避免异常场景下已投入时间丢失。</p>
     */
    private void settleSegment(Long userId, Schedule schedule, LocalDateTime endTime) {
        Long scheduleId = schedule.getId();
        TimeRecord openRecord = findOpenRecord(scheduleId);
        Long redisStart = activeTimerStore.segmentStart(scheduleId).orElse(null);

        LocalDateTime segmentStart = null;
        if (openRecord != null && openRecord.getStartTime() != null) {
            segmentStart = openRecord.getStartTime();
        } else if (redisStart != null) {
            segmentStart = toLocalDateTime(redisStart);
        }

        if (segmentStart == null) {
            // 无任何可结算的片段（例如 PENDING 直接落梭），仅清状态
            activeTimerStore.clearSegment(userId, scheduleId);
            recalcTotalDuration(schedule);
            return;
        }

        int seconds = (int) Math.max(0L, Duration.between(segmentStart, endTime).getSeconds());
        if (openRecord != null) {
            openRecord.setEndTime(endTime);
            openRecord.setDuration(seconds);
            timeRecordMapper.updateById(openRecord);
        } else {
            // DB 中无未结算片段（异常重启导致未落库）：用 Redis 中的开始时间补一条，保证时长不丢
            TimeRecord recovered = new TimeRecord();
            recovered.setScheduleId(scheduleId);
            recovered.setUserId(userId);
            recovered.setStartTime(segmentStart);
            recovered.setEndTime(endTime);
            recovered.setDuration(seconds);
            timeRecordMapper.insert(recovered);
            log.warn("计时片段从 Redis 兜底恢复 scheduleId={}, seconds={}", scheduleId, seconds);
        }

        activeTimerStore.clearSegment(userId, scheduleId);
        recalcTotalDuration(schedule);
    }

    /** 查询未闭合的计时片段 */
    private TimeRecord findOpenRecord(Long scheduleId) {
        return timeRecordMapper.selectOne(new LambdaQueryWrapper<TimeRecord>()
                .eq(TimeRecord::getScheduleId, scheduleId)
                .isNull(TimeRecord::getEndTime)
                .orderByDesc(TimeRecord::getId)
                .last("LIMIT 1"));
    }

    /** 依据全部片段重算实际时长（秒），避免多次累加的舍入误差 */
    private void recalcTotalDuration(Schedule schedule) {
        List<TimeRecord> records = timeRecordMapper.selectList(new LambdaQueryWrapper<TimeRecord>()
                .eq(TimeRecord::getScheduleId, schedule.getId()));
        int totalSeconds = records.stream()
                .mapToInt(r -> r.getDuration() == null ? 0 : r.getDuration())
                .sum();
        schedule.setActualDuration(totalSeconds);
    }

    private LocalDateTime toLocalDateTime(long epochMilli) {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }

    /** 同一用户同时只允许一个进行中的日程，避免计时语义混乱 */
    private void assertNoOtherRunning(Long userId, Long scheduleId) {
        Long activeId = activeTimerStore.activeScheduleId(userId).orElse(null);
        if (activeId == null || activeId.equals(scheduleId)) {
            return;
        }
        Schedule other = scheduleMapper.selectById(activeId);
        if (other != null && ScheduleStatus.IN_PROGRESS.name().equals(other.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT, "已有进行中的日程，请先结束或暂停");
        }
    }

    private ScheduleStatus currentStatus(Schedule schedule) {
        return ScheduleStatus.valueOf(schedule.getStatus());
    }

    private long toEpochMilli(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
