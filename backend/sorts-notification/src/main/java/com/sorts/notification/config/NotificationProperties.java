package com.sorts.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通知服务配置项（前缀 sorts.notification）。
 *
 * @author sorts
 */
@Data
@ConfigurationProperties(prefix = "sorts.notification")
public class NotificationProperties {

    /** 未设置偏好的用户默认提前提醒分钟数 */
    private int defaultAdvanceMinutes = 15;

    /** 未设置偏好的用户默认渠道（逗号分隔） */
    private String defaultChannels = "APP";

    private Reminder reminder = new Reminder();

    /**
     * 定时提醒配置。
     *
     * @author sorts
     */
    @Data
    public static class Reminder {

        /** 总开关 */
        private boolean enabled = true;

        /** 扫描周期（cron） */
        private String cron = "0 * * * * ?";

        /** 过期留痕清理周期（cron），默认每天凌晨 3:30 */
        private String cleanupCron = "0 30 3 * * ?";

        /** 向后看的窗口（分钟），需覆盖用户可配置的最大提前量 */
        private int lookaheadMinutes = 60;

        /** 单次扫描处理的候选上限 */
        private int batchLimit = 200;

        /** 留痕保留天数 */
        private int retentionDays = 30;
    }
}
