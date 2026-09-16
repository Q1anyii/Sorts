package com.sorts.notification.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 提醒渠道（与 api-spec.json 的 ReminderSetting.channels 枚举一致）。
 *
 * <p>当前仅 {@link #APP} 有真实落库实现（站内信即 t_notification 一行）；
 * EMAIL/SMS 属于接入第三方通道后的扩展位，设置里可以勾选，但不会真的发出。</p>
 *
 * @author sorts
 */
@Getter
@AllArgsConstructor
public enum ReminderChannel {

    /** 站内信（已实现） */
    APP("站内信"),

    /** 邮件（预留） */
    EMAIL("邮件"),

    /** 短信（预留） */
    SMS("短信");

    private final String label;

    /** 解析渠道集合，过滤掉未知值；空集合按仅 APP 处理，避免「一个都不勾」把提醒静默关掉 */
    public static Set<ReminderChannel> parse(Set<String> names) {
        if (names == null || names.isEmpty()) {
            return new LinkedHashSet<>(Set.of(APP));
        }
        Set<ReminderChannel> result = new LinkedHashSet<>();
        for (ReminderChannel channel : values()) {
            boolean matched = names.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .anyMatch(name -> channel.name().equalsIgnoreCase(name.trim()));
            if (matched) {
                result.add(channel);
            }
        }
        return result.isEmpty() ? new LinkedHashSet<>(Set.of(APP)) : result;
    }

    /** 序列化为逗号分隔字符串（与库表存储一致） */
    public static String join(Set<ReminderChannel> channels) {
        return channels == null || channels.isEmpty()
                ? "APP"
                : channels.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("APP");
    }

    /** 反序列化，兼容逗号分隔与历史脏数据 */
    public static Set<ReminderChannel> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashSet<>(Set.of(APP));
        }
        return parse(new LinkedHashSet<>(Arrays.asList(raw.split(","))));
    }
}
