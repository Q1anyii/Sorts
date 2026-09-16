package com.sorts.notification.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 通知类型（与 api-spec.json 的 Notification.type 枚举一致）。
 *
 * @author sorts
 */
@Getter
@AllArgsConstructor
public enum NotificationType {

    /** 日程提醒 */
    REMINDER("日程提醒"),

    /** 周期总结（日报/周报/月报生成完毕） */
    SUMMARY("周期总结"),

    /** 系统消息 */
    SYSTEM("系统消息"),

    /** 运营推送（商城上新/活动） */
    PROMOTION("运营推送");

    private final String label;

    /**
     * 宽松解析：未知类型一律按 {@link #SYSTEM} 处理。
     *
     * <p>跨服务调用时类型字符串来自调用方，若因枚举演进而抛异常，
     * 会让一条「无法识别的通知」升级成调用失败——降级为系统消息更符合预期。</p>
     */
    public static NotificationType from(String name) {
        if (name == null || name.isBlank()) {
            return SYSTEM;
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElse(SYSTEM);
    }
}
