package com.sorts.notification.service;

/**
 * 定时提醒服务。
 *
 * <p>把「扫描一次」做成方法而不是直接写在 {@code @Scheduled} 里，
 * 是为了让核心逻辑可以在单测里被直接驱动——调度注解本身不值得测试，
 * 但「什么时候该提醒、什么时候该跳过」值得。</p>
 *
 * @author sorts
 */
public interface ReminderService {

    /**
     * 扫描一次：取出窗口内即将开始的日程，按各用户的提前量与免打扰设置生成提醒。
     *
     * @return 本次实际生成的通知条数
     */
    int scanOnce();

    /**
     * 清理过期提醒留痕。
     *
     * @return 本次删除条数
     */
    int cleanupExpiredLogs();
}
