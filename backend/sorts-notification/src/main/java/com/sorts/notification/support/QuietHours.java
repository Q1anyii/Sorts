package com.sorts.notification.support;

import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;

/**
 * 免打扰时段判断。
 *
 * <p>纯粹的「时间区间包含」看起来很简单，但免打扰时段天然会跨天
 * （23:00 – 07:00 是最常见的配置），用 {@code start < now < end} 直接判断
 * 会得到「永远不在免打扰内」的荒谬结果。跨天情形在这里集中处理一次。</p>
 *
 * @author sorts
 */
public final class QuietHours {

    /**
     * 宽松的 HH:mm 解析器。
     *
     * <p>不能用 {@code LocalTime.parse(text)}：ISO 格式要求小时两位，
     * 而「7:00」这种手输/历史数据非常常见，直接抛异常会让整次提醒扫描中断。</p>
     */
    private static final DateTimeFormatter LENIENT_HH_MM = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .toFormatter();

    private QuietHours() {
    }

    /**
     * 解析 HH:mm（兼容 H:mm 等写法）；非法或为空返回 null。
     *
     * <p>不抛异常：库里的脏数据不应该让整次提醒扫描崩掉。</p>
     */
    public static LocalTime parse(String hhmm) {
        if (!StringUtils.hasText(hhmm)) {
            return null;
        }
        try {
            return LocalTime.parse(hhmm.trim(), LENIENT_HH_MM);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 判断 now 是否落在 [start, end) 区间内。
     *
     * <ul>
     *   <li>start == end：视为「未配置免打扰」而不是 24 小时免打扰
     *       —— 否则用户一旦把起止填成一样，提醒会被永久静默且毫无提示；</li>
     *   <li>start &lt; end：同日区间；</li>
     *   <li>start &gt; end：跨天区间，如 23:00 – 07:00。</li>
     * </ul>
     */
    public static boolean covers(LocalTime start, LocalTime end, LocalTime now) {
        if (start == null || end == null || now == null || start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    /** 文本形态入参，解析失败按「不处于免打扰」处理（宁可多提醒，不可静默丢失） */
    public static boolean covers(String startText, String endText, LocalTime now) {
        return covers(parse(startText), parse(endText), now);
    }
}
