package com.sorts.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sorts.common.exception.BizException;
import com.sorts.schedule.entity.Schedule;
import com.sorts.schedule.enums.ScheduleStatus;
import com.sorts.schedule.mapper.ScheduleMapper;
import com.sorts.schedule.service.AutoSettleService;
import com.sorts.schedule.service.TimerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自动落梭实现。
 *
 * <p>设计：扫描层只负责「找出到期未结的日程」，结算全部复用 {@link TimerService#end}——
 * 时长分段重算、状态机校验、积分发放（失败仅告警）都在 end 链路里，避免逻辑重复。</p>
 *
 * <p>并发说明：end 内部为「查-判-改」，与用户手动落梭存在毫秒级并发窗口；
 * 冲突时 end 抛 CONFLICT，本服务捕获后跳过该条（状态已变更，下次扫描自然不再命中）。
 * 个人项目规模下该窗口可接受；若上多实例，可再把 end 的状态更新改成条件更新。</p>
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoSettleServiceImpl implements AutoSettleService {

    private final ScheduleMapper scheduleMapper;

    private final TimerService timerService;

    /** 单次扫描上限，防止一次扫出过多拖长调度周期 */
    private static final int BATCH_LIMIT = 100;

    @Override
    public int scanExpired() {
        // 到期判定：计划开始时间 + 计划时长（分钟）早于当前时间。
        // planned_duration 为 NULL 时表达式结果为 NULL 不命中，天然跳过脏数据；
        // 逻辑删除由 MyBatis-Plus 全局配置自动追加 deleted = 0。
        List<Schedule> expired = scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .in(Schedule::getStatus, ScheduleStatus.IN_PROGRESS.name(), ScheduleStatus.PAUSED.name())
                .apply("planned_start_time + INTERVAL planned_duration MINUTE < NOW()")
                .orderByAsc(Schedule::getId)
                .last("LIMIT " + BATCH_LIMIT));

        int settled = 0;
        for (Schedule s : expired) {
            try {
                timerService.end(s.getUserId(), s.getId());
                settled++;
            } catch (BizException e) {
                // 状态已被用户手动结束/取消等：跳过，不算失败
                log.info("日程 {} 自动落梭跳过：{}", s.getId(), e.getMessage());
            } catch (Exception e) {
                // 单条失败不影响其他条目；事务已回滚，下次扫描天然重试
                log.error("日程 {} 自动落梭失败", s.getId(), e);
            }
        }
        return settled;
    }
}
