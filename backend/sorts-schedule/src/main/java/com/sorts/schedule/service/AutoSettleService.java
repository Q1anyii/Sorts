package com.sorts.schedule.service;

/**
 * 自动落梭：到计划结束时间仍在进行中/暂停的日程，由系统代为结算。
 *
 * <p>结束时间 = plannedStartTime + plannedDuration（分钟），由服务端确定性计算，
 * 不依赖客户端计时；结算复用 {@link TimerService#end} 链路（分段重算时长 +
 * 奖励发放失败仅告警），避免与手动落梭逻辑重复。</p>
 *
 * @author sorts
 */
public interface AutoSettleService {

    /**
     * 扫描「计划结束时间已过、状态仍为进行中/暂停」的日程并自动落梭。
     *
     * @return 本次实际自动结算的条数
     */
    int scanExpired();
}
