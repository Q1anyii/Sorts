package com.sorts.notification.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通知类型与提醒渠道枚举的单测：重点在「宽松解析」的降级语义。
 *
 * @author sorts
 */
@DisplayName("通知枚举：类型降级与渠道解析")
class NotificationEnumTest {

    @Test
    @DisplayName("NotificationType：大小写不敏感，未知值降级为 SYSTEM 而不是抛异常")
    void notificationTypeIsLenient() {
        assertEquals(NotificationType.REMINDER, NotificationType.from(" reminder "));
        assertEquals(NotificationType.SUMMARY, NotificationType.from("SUMMARY"));
        // 跨服务传错类型不应该让一条通知升级成调用失败
        assertEquals(NotificationType.SYSTEM, NotificationType.from("what-is-this"));
        assertEquals(NotificationType.SYSTEM, NotificationType.from(null));
        assertEquals(NotificationType.SYSTEM, NotificationType.from("  "));
    }

    @Test
    @DisplayName("ReminderChannel：过滤未知值，全未知或空时回落到 APP")
    void channelParseFiltersUnknown() {
        assertEquals(Set.of(ReminderChannel.APP, ReminderChannel.EMAIL),
                ReminderChannel.parse(new LinkedHashSet<>(List.of("app", "EMAIL", "WECHAT"))));
        // 一个都不勾不应把提醒静默关掉
        assertEquals(Set.of(ReminderChannel.APP), ReminderChannel.parse(Set.of()));
        assertEquals(Set.of(ReminderChannel.APP), ReminderChannel.parse(Set.of("WECHAT")));
        assertEquals(Set.of(ReminderChannel.APP), ReminderChannel.parse(null));
    }

    @Test
    @DisplayName("ReminderChannel：逗号分隔的序列化与反序列化互为逆运算")
    void channelRoundTrip() {
        String joined = ReminderChannel.join(new LinkedHashSet<>(
                List.of(ReminderChannel.APP, ReminderChannel.SMS)));
        assertTrue(joined.contains("APP") && joined.contains("SMS"));
        assertEquals(Set.of(ReminderChannel.APP, ReminderChannel.SMS), ReminderChannel.split(joined));

        // 历史脏数据（空串、脏值）按默认处理
        assertEquals(Set.of(ReminderChannel.APP), ReminderChannel.split(""));
        assertEquals(Set.of(ReminderChannel.APP), ReminderChannel.split(null));
        assertEquals(Set.of(ReminderChannel.APP), ReminderChannel.split(" , "));
        assertEquals("APP", ReminderChannel.join(null));
    }
}
