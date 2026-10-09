package com.sorts.schedule.service.impl;

import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.schedule.entity.Schedule;
import com.sorts.schedule.enums.ScheduleStatus;
import com.sorts.schedule.mapper.ScheduleMapper;
import com.sorts.schedule.service.TimerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自动落梭单元测试。
 *
 * <p>重点覆盖：过期日程逐条结算、状态冲突跳过不计数、单条失败不中断其余条目、
 * 空结果不触发任何结算。</p>
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoSettleServiceImplTest {

    private static final Long USER_ID = 7L;

    @Mock
    private ScheduleMapper scheduleMapper;

    @Mock
    private TimerService timerService;

    @InjectMocks
    private AutoSettleServiceImpl autoSettleService;

    private Schedule schedule(Long id, String status) {
        Schedule s = new Schedule();
        s.setId(id);
        s.setUserId(USER_ID);
        s.setStatus(status);
        return s;
    }

    @Test
    @DisplayName("A1 正常结算：过期列表逐条走 end，返回成功条数")
    void settleAllSuccess() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of(
                schedule(1L, ScheduleStatus.IN_PROGRESS.name()),
                schedule(2L, ScheduleStatus.PAUSED.name()),
                schedule(3L, ScheduleStatus.IN_PROGRESS.name())));

        int settled = autoSettleService.scanExpired();

        assertEquals(3, settled);
        verify(timerService, times(3)).end(anyLong(), anyLong());
    }

    @Test
    @DisplayName("A2 状态冲突跳过：end 抛 CONFLICT（用户已手动结束）不计成功、不中断")
    void conflictSkipped() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of(
                schedule(1L, ScheduleStatus.IN_PROGRESS.name()),
                schedule(2L, ScheduleStatus.IN_PROGRESS.name()),
                schedule(3L, ScheduleStatus.PAUSED.name())));
        // 第 2 条被用户手动结束，状态机拒绝
        doThrow(new BizException(ErrorCode.CONFLICT, "状态已变更")).when(timerService)
                .end(USER_ID, 2L);

        int settled = autoSettleService.scanExpired();

        assertEquals(2, settled);
        verify(timerService).end(USER_ID, 1L);
        verify(timerService).end(USER_ID, 2L);
        verify(timerService).end(USER_ID, 3L);
    }

    @Test
    @DisplayName("A3 单条系统异常不中断其余：事务回滚，下次扫描重试")
    void singleFailureContinuesOthers() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of(
                schedule(1L, ScheduleStatus.IN_PROGRESS.name()),
                schedule(2L, ScheduleStatus.IN_PROGRESS.name())));
        doThrow(new RuntimeException("DB down")).when(timerService).end(USER_ID, 1L);

        int settled = autoSettleService.scanExpired();

        assertEquals(1, settled);
        verify(timerService).end(USER_ID, 2L);
    }

    @Test
    @DisplayName("A4 空列表：无到期日程，不触发任何结算")
    void emptyListNoop() {
        when(scheduleMapper.selectList(any())).thenReturn(List.of());

        int settled = autoSettleService.scanExpired();

        assertEquals(0, settled);
        verify(timerService, never()).end(anyLong(), anyLong());
    }
}
