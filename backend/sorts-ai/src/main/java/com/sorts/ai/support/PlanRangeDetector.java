package com.sorts.ai.support;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * 从用户描述中解析相对时间范围（「未来一周」「下周」「本周」「本月」等）。
 *
 * <p>设计原则：AI 调用层必须注入确定的日期区间，模型只负责在区间内排布事项，
 * 不允许靠模型记忆猜「今天是几号」——日期一旦猜错，整周计划都会串位。</p>
 *
 * @author sorts
 */
public final class PlanRangeDetector {

    /** 一周（含今天）跨度：startDate..endDate 共 7 天 */
    private static final int WEEK_SPAN_DAYS = 6;

    private PlanRangeDetector() {
    }

    /**
     * 解析结果：matched=true 表示识别出相对时间范围；否则调用方回退默认单日。
     *
     * @param userPrompt 用户原始描述
     * @param today      当前日期（服务端权威时间）
     */
    public static Range parse(String userPrompt, LocalDate today) {
        String text = userPrompt == null ? "" : userPrompt.trim();
        LocalDate start;
        LocalDate end;

        // 优先级从宽到窄：先匹配「本周/本月/下周」这类整段区间，再匹配「未来一周」，
        // 最后匹配单日（今天/明天）。单日也是区间（start=end），便于统一处理。
        if (contains(text, "本月", "这个月")) {
            start = today.withDayOfMonth(1);
            end = start.withDayOfMonth(start.lengthOfMonth());
        } else if (contains(text, "下周")) {
            start = today.with(DayOfWeek.MONDAY).plusWeeks(1);
            end = start.plusDays(WEEK_SPAN_DAYS);
        } else if (contains(text, "本周", "这一周")) {
            start = today.with(DayOfWeek.MONDAY);
            end = start.plusDays(WEEK_SPAN_DAYS);
        } else if (contains(text, "未来一周", "接下来一周", "这周计划", "一周规划", "一周计划", "下个星期", "下周计划")) {
            start = today;
            end = today.plusDays(WEEK_SPAN_DAYS);
        } else if (contains(text, "今天到一周", "今天起一周", "今天开始的一周")) {
            start = today;
            end = today.plusDays(WEEK_SPAN_DAYS);
        } else if (contains(text, "明天", "明日")) {
            start = today.plusDays(1);
            end = start;
        } else if (contains(text, "今天", "今日", "当天")) {
            start = today;
            end = start;
        } else {
            return new Range(false, null, null);
        }
        return new Range(true, start, end);
    }

    private static boolean contains(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 解析结果：包含区间与是否命中。 */
    public record Range(boolean matched, LocalDate startDate, LocalDate endDate) {
    }
}
