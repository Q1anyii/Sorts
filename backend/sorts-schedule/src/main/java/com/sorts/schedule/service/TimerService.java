package com.sorts.schedule.service;

import com.sorts.schedule.dto.ScheduleVO;

/**
 * 计时服务：日程的「穿梭中 → 停梭 → 续梭 → 落梭」状态机。
 *
 * @author sorts
 */
public interface TimerService {

    /** 开始计时（PENDING / PAUSED → IN_PROGRESS） */
    ScheduleVO start(Long userId, Long scheduleId);

    /** 暂停计时（IN_PROGRESS → PAUSED），结算当前片段时长 */
    ScheduleVO pause(Long userId, Long scheduleId);

    /** 恢复计时（PAUSED → IN_PROGRESS），开启新片段 */
    ScheduleVO resume(Long userId, Long scheduleId);

    /** 结束计时（PENDING / IN_PROGRESS / PAUSED → COMPLETED），结算时长并发放光阴砂 */
    ScheduleVO end(Long userId, Long scheduleId);

    /** 取消日程（非终态 → CANCELLED） */
    ScheduleVO cancel(Long userId, Long scheduleId);

    /** 查询用户当前穿梭中的日程，无则返回 null */
    ScheduleVO getActive(Long userId);
}
