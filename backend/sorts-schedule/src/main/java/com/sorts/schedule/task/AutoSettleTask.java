package com.sorts.schedule.task;

import com.sorts.schedule.service.AutoSettleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 自动落梭调度：每分钟扫描一次「计划结束时间已过」的进行中/暂停日程。
 *
 * <p>薄层模式（与 sorts-notification 的 ReminderScanTask 一致）：调度本身不写业务逻辑，
 * 全部委托 {@link AutoSettleService#scanExpired()}；异常兜住防止调度线程被杀死。</p>
 *
 * @author sorts
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoSettleTask {

    private final AutoSettleService autoSettleService;

    @Scheduled(cron = "${sorts.schedule.auto-settle.cron:0 * * * * ?}")
    public void scanExpired() {
        try {
            int settled = autoSettleService.scanExpired();
            if (settled > 0) {
                log.info("自动落梭：本次结算 {} 条过期日程", settled);
            }
        } catch (Exception e) {
            // 扫描级异常（DB 不可用等）不能中断后续周期
            log.error("自动落梭扫描异常", e);
        }
    }
}
