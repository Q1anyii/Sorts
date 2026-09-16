package com.sorts.schedule.service.impl;

import com.sorts.common.exception.BizException;
import com.sorts.schedule.dto.ScheduleVO;
import com.sorts.schedule.entity.Schedule;
import com.sorts.schedule.entity.TimeRecord;
import com.sorts.schedule.enums.ScheduleStatus;
import com.sorts.schedule.mapper.ScheduleMapper;
import com.sorts.schedule.mapper.TimeRecordMapper;
import com.sorts.schedule.repository.ActiveTimerStore;
import com.sorts.schedule.service.PointsRewardService;
import com.sorts.schedule.service.ScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 计时状态机单元测试：穿梭 / 停梭 / 续梭 / 落梭 / 取消。
 *
 * <p>重点覆盖：状态非法流转被拒、分段时长累加与重算、Redis 兜底恢复、
 * 以及落梭奖励不因失败而阻断主流程。</p>
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimerServiceImplTest {

    private static final Long USER_ID = 7L;

    private static final Long SCHEDULE_ID = 100L;

    @Mock
    private ScheduleService scheduleService;

    @Mock
    private ScheduleMapper scheduleMapper;

    @Mock
    private TimeRecordMapper timeRecordMapper;

    @Mock
    private ActiveTimerStore activeTimerStore;

    @Mock
    private PointsRewardService pointsRewardService;

    @InjectMocks
    private TimerServiceImpl timerService;

    private Schedule schedule;

    @BeforeEach
    void setUp() {
        schedule = new Schedule();
        schedule.setId(SCHEDULE_ID);
        schedule.setUserId(USER_ID);
        schedule.setTitle("织一段代码");
        schedule.setStatus(ScheduleStatus.PENDING.name());
        schedule.setActualDuration(0);
        when(scheduleService.requireOwned(USER_ID, SCHEDULE_ID)).thenReturn(schedule);
    }

    @Test
    @DisplayName("start：PENDING → IN_PROGRESS，写入首次实际开始时间并开启片段")
    void startFromPending() {
        ScheduleVO vo = timerService.start(USER_ID, SCHEDULE_ID);

        assertEquals(ScheduleStatus.IN_PROGRESS.name(), vo.getStatus());
        assertNotNull(schedule.getActualStartTime());
        verify(timeRecordMapper).insert(ArgumentMatchers.<TimeRecord>any());
        verify(activeTimerStore).startSegment(eq(USER_ID), eq(SCHEDULE_ID), anyLong());
    }

    @Test
    @DisplayName("start：已落梭的日程不可再开始")
    void startRejectsCompleted() {
        schedule.setStatus(ScheduleStatus.COMPLETED.name());

        BizException e = assertThrows(BizException.class, () -> timerService.start(USER_ID, SCHEDULE_ID));
        assertTrue(e.getMessage().contains("不可开始计时"));
        verify(timeRecordMapper, never()).insert(ArgumentMatchers.<TimeRecord>any());
    }

    @Test
    @DisplayName("start：已有其他日程穿梭中时拒绝，保证同时只有一个进行中")
    void startRejectsWhenAnotherRunning() {
        Schedule other = new Schedule();
        other.setId(999L);
        other.setStatus(ScheduleStatus.IN_PROGRESS.name());
        when(activeTimerStore.activeScheduleId(USER_ID)).thenReturn(Optional.of(999L));
        when(scheduleMapper.selectById(999L)).thenReturn(other);

        BizException e = assertThrows(BizException.class, () -> timerService.start(USER_ID, SCHEDULE_ID));
        assertTrue(e.getMessage().contains("已有进行中的日程"));
    }

    @Test
    @DisplayName("start：Redis 活跃标记指向自己时允许（重复 start 不误伤）")
    void startAllowsOwnActiveMarker() {
        when(activeTimerStore.activeScheduleId(USER_ID)).thenReturn(Optional.of(SCHEDULE_ID));
        schedule.setStatus(ScheduleStatus.PAUSED.name());

        assertEquals(ScheduleStatus.IN_PROGRESS.name(), timerService.start(USER_ID, SCHEDULE_ID).getStatus());
    }

    @Test
    @DisplayName("pause：结算片段秒数，状态转 PAUSED 并清理 Redis 活跃标记")
    void pauseSettlesSegment() {
        schedule.setStatus(ScheduleStatus.IN_PROGRESS.name());
        TimeRecord open = openRecord(LocalDateTime.now().minusMinutes(30));
        when(timeRecordMapper.selectOne(any())).thenReturn(open);
        when(timeRecordMapper.selectList(any())).thenReturn(List.of(open));

        ScheduleVO vo = timerService.pause(USER_ID, SCHEDULE_ID);

        assertEquals(ScheduleStatus.PAUSED.name(), vo.getStatus());
        assertNotNull(open.getEndTime());
        int seconds = vo.getActualDuration();
        assertTrue(seconds >= 1799 && seconds <= 1801, "片段秒数应约等于 1800，实际=" + seconds);
        verify(activeTimerStore).clearSegment(USER_ID, SCHEDULE_ID);
    }

    @Test
    @DisplayName("pause：仅穿梭中的日程可暂停，PENDING 直接拒绝")
    void pauseRejectsPending() {
        BizException e = assertThrows(BizException.class, () -> timerService.pause(USER_ID, SCHEDULE_ID));
        assertTrue(e.getMessage().contains("仅穿梭中"));
    }

    @Test
    @DisplayName("resume：PAUSED → IN_PROGRESS，开启新片段")
    void resumeOpensNewSegment() {
        schedule.setStatus(ScheduleStatus.PAUSED.name());

        ScheduleVO vo = timerService.resume(USER_ID, SCHEDULE_ID);

        assertEquals(ScheduleStatus.IN_PROGRESS.name(), vo.getStatus());
        verify(timeRecordMapper).insert(ArgumentMatchers.<TimeRecord>any());
    }

    @Test
    @DisplayName("resume：未暂停的日程不可续梭")
    void resumeRejectsNonPaused() {
        assertThrows(BizException.class, () -> timerService.resume(USER_ID, SCHEDULE_ID));
    }

    @Test
    @DisplayName("end：结算当前片段 + 重算总时长 + 写入结束时间 + 发放落梭奖励")
    void endCompletesAndRewards() {
        schedule.setStatus(ScheduleStatus.IN_PROGRESS.name());
        TimeRecord open = openRecord(LocalDateTime.now().minusMinutes(10));
        when(timeRecordMapper.selectOne(any())).thenReturn(open);
        // 历史片段 1200s + 当前片段约 600s
        TimeRecord history = closedRecord(1200);
        when(timeRecordMapper.selectList(any())).thenReturn(List.of(history, open));

        ScheduleVO vo = timerService.end(USER_ID, SCHEDULE_ID);

        assertEquals(ScheduleStatus.COMPLETED.name(), vo.getStatus());
        assertNotNull(schedule.getActualEndTime());
        assertTrue(vo.getActualDuration() >= 1799 && vo.getActualDuration() <= 1801,
                "总时长应为两段之和约 1800s，实际=" + vo.getActualDuration());
        verify(pointsRewardService).rewardOnComplete(USER_ID, SCHEDULE_ID);
        verify(activeTimerStore).clearSegment(USER_ID, SCHEDULE_ID);
    }

    @Test
    @DisplayName("end：PENDING 未开始也可直接落梭，按 0 时长处理且不结算片段")
    void endFromPendingWithoutSegment() {
        ScheduleVO vo = timerService.end(USER_ID, SCHEDULE_ID);

        assertEquals(ScheduleStatus.COMPLETED.name(), vo.getStatus());
        assertEquals(0, vo.getActualDuration());
        verify(timeRecordMapper, never()).insert(ArgumentMatchers.<TimeRecord>any());
        verify(pointsRewardService).rewardOnComplete(USER_ID, SCHEDULE_ID);
    }

    @Test
    @DisplayName("end：已取消 / 已完成的日程不可重复落梭（防重复发奖励）")
    void endRejectsFinalStatus() {
        schedule.setStatus(ScheduleStatus.CANCELLED.name());

        assertThrows(BizException.class, () -> timerService.end(USER_ID, SCHEDULE_ID));
        verify(pointsRewardService, never()).rewardOnComplete(anyLong(), anyLong());
    }

    @Test
    @DisplayName("end：Redis 兜底——DB 无未闭合片段时用 Redis 开始时间补记，已投入时间不丢")
    void endRecoversSegmentFromRedis() {
        schedule.setStatus(ScheduleStatus.IN_PROGRESS.name());
        when(timeRecordMapper.selectOne(any())).thenReturn(null);
        long startEpoch = LocalDateTime.now().minusMinutes(5)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        when(activeTimerStore.segmentStart(SCHEDULE_ID)).thenReturn(Optional.of(startEpoch));
        when(timeRecordMapper.selectList(any())).thenReturn(List.of());

        ScheduleVO vo = timerService.end(USER_ID, SCHEDULE_ID);

        verify(timeRecordMapper).insert(ArgumentMatchers.<TimeRecord>any());
        assertEquals(0, vo.getActualDuration());
    }

    @Test
    @DisplayName("end：DB 未闭合片段是权威来源——即使 Redis 键已丢失，也能按 DB 开始时间结算")
    void endUsesDbRecordAsSourceOfTruth() {
        schedule.setStatus(ScheduleStatus.IN_PROGRESS.name());
        TimeRecord open = openRecord(LocalDateTime.now().minusMinutes(5));
        when(timeRecordMapper.selectOne(any())).thenReturn(open);
        when(activeTimerStore.segmentStart(SCHEDULE_ID)).thenReturn(Optional.empty());
        when(timeRecordMapper.selectList(any())).thenReturn(List.of(open));

        ScheduleVO vo = timerService.end(USER_ID, SCHEDULE_ID);

        assertNotNull(open.getEndTime());
        assertTrue(open.getDuration() >= 299 && open.getDuration() <= 301,
                "应按 DB 片段开始时间结算约 300s，实际=" + open.getDuration());
        assertEquals(vo.getActualDuration().intValue(), open.getDuration().intValue());
        verify(timeRecordMapper).updateById(open);
        verify(timeRecordMapper, never()).insert(ArgumentMatchers.<TimeRecord>any());
    }

    @Test
    @DisplayName("end：既无 DB 片段也无 Redis 记录时仅清理状态，不产生空片段")
    void endWithoutAnySegment() {
        schedule.setStatus(ScheduleStatus.IN_PROGRESS.name());
        when(timeRecordMapper.selectOne(any())).thenReturn(null);
        when(activeTimerStore.segmentStart(SCHEDULE_ID)).thenReturn(Optional.empty());
        when(timeRecordMapper.selectList(any())).thenReturn(List.of());

        ScheduleVO vo = timerService.end(USER_ID, SCHEDULE_ID);

        assertEquals(0, vo.getActualDuration().intValue());
        verify(timeRecordMapper, never()).insert(ArgumentMatchers.<TimeRecord>any());
        verify(activeTimerStore).clearSegment(USER_ID, SCHEDULE_ID);
    }

    @Test
    @DisplayName("cancel：取消时保留已投入时间痕迹")
    void cancelKeepsInvestedTime() {
        schedule.setStatus(ScheduleStatus.IN_PROGRESS.name());
        TimeRecord open = openRecord(LocalDateTime.now().minusMinutes(3));
        when(timeRecordMapper.selectOne(any())).thenReturn(open);
        when(timeRecordMapper.selectList(any())).thenReturn(List.of(open));

        ScheduleVO vo = timerService.cancel(USER_ID, SCHEDULE_ID);

        assertEquals(ScheduleStatus.CANCELLED.name(), vo.getStatus());
        assertNotNull(open.getEndTime());
        // 取消不发放奖励
        verify(pointsRewardService, never()).rewardOnComplete(anyLong(), anyLong());
    }

    @Test
    @DisplayName("cancel：已完成的日程不可取消")
    void cancelRejectsCompleted() {
        schedule.setStatus(ScheduleStatus.COMPLETED.name());
        assertThrows(BizException.class, () -> timerService.cancel(USER_ID, SCHEDULE_ID));
    }

    @Test
    @DisplayName("getActive：无进行中日程时返回 null")
    void getActiveReturnsNullWhenIdle() {
        when(scheduleMapper.selectOne(any())).thenReturn(null);
        assertNull(timerService.getActive(USER_ID));
    }

    @Test
    @DisplayName("getActive：返回进行中的日程")
    void getActiveReturnsRunning() {
        schedule.setStatus(ScheduleStatus.IN_PROGRESS.name());
        when(scheduleMapper.selectOne(any())).thenReturn(schedule);

        ScheduleVO vo = timerService.getActive(USER_ID);

        assertNotNull(vo);
        assertEquals(SCHEDULE_ID, vo.getId());
    }

    private TimeRecord openRecord(LocalDateTime start) {
        TimeRecord record = new TimeRecord();
        record.setId(1L);
        record.setScheduleId(SCHEDULE_ID);
        record.setUserId(USER_ID);
        record.setStartTime(start);
        record.setDuration(0);
        return record;
    }

    private TimeRecord closedRecord(int seconds) {
        TimeRecord record = new TimeRecord();
        record.setId(2L);
        record.setScheduleId(SCHEDULE_ID);
        record.setUserId(USER_ID);
        record.setStartTime(LocalDateTime.now().minusHours(2));
        record.setEndTime(LocalDateTime.now().minusHours(1));
        record.setDuration(seconds);
        return record;
    }
}
