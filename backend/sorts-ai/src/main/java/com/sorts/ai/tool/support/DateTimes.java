package com.sorts.ai.tool.support;

import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 模型输出时间的容错解析。
 *
 * <p>即便 schema 里写明了格式，模型仍会时不时给出 {@code 2026/09/16 09:00}、
 * {@code 2026-09-16T09:00}、{@code 9:00} 这类变体。这里做「宽松解析」而不是直接报错，
 * 因为这些差异对用户语义完全相同，报错只会让一次好端端的规划失败。</p>
 *
 * @author sorts
 */
public final class DateTimes {

    private static final DateTimeFormatter SPACE_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter SPACE_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private DateTimes() {
    }

    /** 解析日期，支持 yyyy-MM-dd 与 yyyy/MM/dd */
    public static LocalDate parseDate(String raw, String fieldName) {
        String value = normalize(raw);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.replace('/', '-'));
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, fieldName + " 不是合法日期：" + raw);
        }
    }

    /** 解析日期时间，兼容 ISO 与空格分隔两种写法 */
    public static LocalDateTime parseDateTime(String raw, String fieldName) {
        String value = normalize(raw);
        if (value == null) {
            return null;
        }
        String candidate = value.replace('/', '-').replace(' ', 'T');
        try {
            return LocalDateTime.parse(candidate);
        } catch (DateTimeParseException ignored) {
            // 继续尝试下面的兜底格式
        }
        for (DateTimeFormatter formatter : new DateTimeFormatter[]{SPACE_SECONDS, SPACE_MINUTES}) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 换下一个格式
            }
        }
        throw new BizException(ErrorCode.PARAM_ERROR,
                fieldName + " 不是合法时间（建议 yyyy-MM-dd HH:mm:ss）：" + raw);
    }

    /**
     * 把「只有时分」的建议时间（HH:mm）落到目标日期上。
     *
     * <p>AI 规划返回的 suggestedStart 就是这种形态，采纳时需要拼上日期才能建日程。</p>
     */
    public static LocalDateTime atTimeOn(LocalDate date, String hhmm) {
        String value = normalize(hhmm);
        if (value == null) {
            return null;
        }
        try {
            String normalized = value.length() == 4 && value.indexOf(':') == 1 ? "0" + value : value;
            return date.atTime(java.time.LocalTime.parse(normalized));
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "建议开始时间不是合法时间：" + hhmm);
        }
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
