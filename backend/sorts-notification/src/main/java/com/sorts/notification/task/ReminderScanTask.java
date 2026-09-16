package com.sorts.notification.task;

import com.sorts.notification.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时提醒调度入口。
 *
 * <p>这一层刻意做得极薄：只负责「按点调用」和「兜住异常」。
 * 业务判断全在 {@link ReminderService} 里，因此可以用单测直接驱动，
 * 不需要为了测试而启动调度器。</p>
 *
 * @author sorts
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScanTask {

    private final ReminderService reminderService;

    /** 周期性扫描即将开始的日程并生成提醒 */
    @Scheduled(cron = "${sorts.notification.reminder.cron:0 * * * * ?}")
    public void scanReminders() {
        try {
            reminderService.scanOnce();
        } catch (Exception e) {
            // 调度任务里的异常会静默终止后续触发，必须在这里兜住并记录
            log.error("定时提醒扫描异常", e);
        }
    }

    /** 低频清理过期留痕（留痕只为去重，不需要长期保留） */
    @Scheduled(cron = "${sorts.notification.reminder.cleanup-cron:0 30 3 * * ?}")
    public void cleanupReminderLogs() {
        try {
            reminderService.cleanupExpiredLogs();
        } catch (Exception e) {
            log.error("提醒留痕清理异常", e);
        }
    }
}
