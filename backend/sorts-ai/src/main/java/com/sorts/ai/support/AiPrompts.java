package com.sorts.ai.support;

import com.sorts.ai.client.dto.ScheduleDto;
import com.sorts.ai.client.dto.StatisticsSummaryDto;
import com.sorts.ai.client.dto.TagStatDto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;

/**
 * 提示词集中管理。
 *
 * <p>为什么把提示词单独抽出来，而不是散在 Service 里拼字符串：
 * 提示词是本项目里最需要反复打磨、也最容易在重构中被无意改坏的部分。
 * 集中一处才能被评审、被对比、被单测断言（尤其是「必须包含约束条款」这类检查）。</p>
 *
 * @author sorts
 */
public final class AiPrompts {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 梭灵人格与通用铁律 */
    public static final String CHAT_SYSTEM = """
            你是「梭灵」，梭子（SORTS）智能日程平台内置的助手。
            梭子的世界观里：时间像丝线，日程编排如同织锦；完成一段专注叫「穿梭」，结束叫「落梭」，
            积分叫「光阴砂」。你可以自然地使用这些词，但不要过度堆砌意象，更不要因此影响表达的准确性。

            行为准则：
            1. 用简体中文回答，语气亲切、克制，不说套话，不奉承。
            2. 需要事实时先调用工具查询，绝不允许凭猜测编造用户的日程、统计或积分数据。
            3. 查询不到就说查询不到，并给出下一步建议；不要用「大概」「可能」糊弄。
            4. 涉及创建/修改数据时，必须先向用户复述一遍要点并征得同意，
               不能仅凭自己的推断就写入。
            5. 回答用 Markdown，简洁分段；列表不超过 7 条。
            """;

    private AiPrompts() {
    }

    /** 规划生成的系统提示（偏理性，降低发散） */
    public static String planSystem() {
        return """
                你是「梭灵」，负责把用户一句自然语言描述拆解为可直接执行的日程建议。
                你输出的内容会被程序解析为结构化数据，因此格式必须严格符合要求。
                """;
    }

    /**
     * 规划生成的用户提示（单日：兼容旧调用，等价于 start=end=targetDate 的多日版本）。
     */
    public static String planPrompt(String userPrompt, LocalDate targetDate,
                                    int startHour, int endHour, int defaultDuration) {
        return planPrompt(userPrompt, targetDate, targetDate, startHour, endHour, defaultDuration);
    }

    /**
     * 规划生成的用户提示（多日：覆盖 [start, end] 闭区间）。
     *
     * <p>关键约束：日期由服务端注入（今天是几号、允许落在哪几天），模型不得自行推断；
     * 每条建议必须带 {@code date}；多日时按天均匀分布，不允许把整周计划堆在一天。</p>
     */
    public static String planPrompt(String userPrompt, LocalDate start, LocalDate end,
                                    int startHour, int endHour, int defaultDuration) {
        LocalDate today = LocalDate.now();
        boolean multiDay = !start.equals(end);
        String dateRule = multiDay
                ? """
                  - 每天 2-5 条，按天均匀分布，禁止把整周计划全部堆在第一天；
                  - 建议总条数控制在 7-30 条，覆盖从 %s 到 %s 的每一天。
                  """.formatted(start.format(DAY), end.format(DAY))
                : """
                  - 共 3-8 条，全部安排在 %s 这一天。
                  """.formatted(start.format(DAY));
        return """
                今天是 %s（服务器时间）。
                目标日期区间：%s 至 %s（含首尾两天）。
                可安排时段：%02d:00 - %02d:00
                用户未说明时长时，单条按 %d 分钟安排。

                用户的原始描述：
                \"\"\"
                %s
                \"\"\"

                请把描述拆解为具体的日程建议。严格只输出一个 JSON 对象，不要输出解释文字，
                不要使用 Markdown 代码块包裹：
                {"suggestions":[{"date":"yyyy-MM-dd","title":"日程标题","description":"简短说明",
                "suggestedStart":"HH:mm","duration":60,"priority":"MEDIUM","tags":["标签"],"reason":"这样安排的理由"}]}

                硬性要求：
                - 每条建议必须给出 date，格式 yyyy-MM-dd，且只能落在上述目标日期区间内；
                - %s；
                - suggestedStart 必须是 HH:mm（24 小时制），且全部落在可安排时段内；
                - 同一天内相邻两条时间不得重叠，请为转场预留余量；
                - duration 为分钟整数；priority 只能是 LOW、MEDIUM、HIGH 之一；
                - title 不超过 30 字，直接说做什么，不要出现「建议」「可能」等字样。
                """.formatted(today.format(DAY), start.format(DAY), end.format(DAY),
                startHour, endHour, defaultDuration, userPrompt,
                multiDay
                        ? "今天是 " + today.format(DAY) + "，也在区间内，可正常安排任务"
                        : "今天是 " + today.format(DAY) + "，是唯一可安排的一天");
    }

    /** 周期总结的系统提示 */
    public static String reportSystem() {
        return """
                你是「梭灵」，负责依据客观统计数据写周期复盘。
                你的结论必须能从给定数据中推出。数据没体现的内容一律不写，
                宁可少写也不要为了篇幅补充猜测性内容。
                """;
    }

    /**
     * 总结报告的用户提示。
     *
     * <p>明细只给「已经发生的事实」，并把时长换算成分钟——
     * 直接给模型秒数会让它把 7200 秒说成「7200 分钟」。</p>
     */
    public static String reportPrompt(String typeLabel, LocalDate start, LocalDate end,
                                     StatisticsSummaryDto summary, List<ScheduleDto> schedules) {
        StringBuilder builder = new StringBuilder();
        builder.append("复盘类型：").append(typeLabel).append('\n');
        builder.append("统计区间：").append(start.format(DAY));
        if (!start.equals(end)) {
            builder.append(" ~ ").append(end.format(DAY));
        }
        builder.append("\n\n【汇总数据】\n");
        if (summary == null) {
            builder.append("（统计服务未返回数据）\n");
        } else {
            builder.append("日程总数：").append(nullSafe(summary.getTotalSchedules()))
                    .append("，已完成：").append(nullSafe(summary.getCompletedSchedules()))
                    .append("，完成率：").append(percent(summary.getCompletionRate()))
                    .append("\n总专注时长：").append(minutes(summary.getTotalFocusTime()))
                    .append(" 分钟，日均：").append(minutes(summary.getAvgFocusTime())).append(" 分钟\n");
            builder.append("标签分布：");
            List<TagStatDto> tags = summary.getTagDistribution();
            if (tags == null || tags.isEmpty()) {
                builder.append("（无标签数据）");
            } else {
                StringJoiner joiner = new StringJoiner("、");
                for (TagStatDto tag : tags) {
                    joiner.add("%s %s 分钟（%d 条）".formatted(
                            tag.getTag(), minutes(tag.getTotalDuration()), nullSafe(tag.getCount())));
                }
                builder.append(joiner);
            }
            builder.append('\n');
        }

        builder.append("\n【日程明细】\n");
        if (schedules == null || schedules.isEmpty()) {
            builder.append("（该区间没有日程记录）\n");
        } else {
            for (ScheduleDto schedule : schedules) {
                builder.append("- ").append(schedule.getPlannedStartTime() == null
                                ? "未排期" : schedule.getPlannedStartTime().toString().substring(11, 16))
                        .append(' ').append(schedule.getTitle())
                        .append("（状态 ").append(schedule.getStatus())
                        .append("，计划 ").append(minutesOfPlan(schedule.getPlannedDuration()))
                        .append(" 分钟，实际 ").append(minutes(schedule.getActualDuration())).append(" 分钟）");
                if (schedule.getTags() != null && !schedule.getTags().isEmpty()) {
                    builder.append(" 标签=").append(String.join("/", schedule.getTags()));
                }
                builder.append('\n');
            }
        }

        builder.append("""

                请只输出一个 JSON 对象，不要输出解释文字，不要用 Markdown 代码块包裹：
                {"title":"报告标题（不超过 20 字）",
                 "content":"Markdown 正文",
                 "highlights":["亮点1","亮点2"],
                 "suggestions":["改进建议1","改进建议2"]}

                正文要求：
                - 结构为「整体评价 → 时间分配 → 亮点 → 改进建议」四段，每段 2-4 句；
                - 引用数字时使用上面给出的分钟数，不要自行换算或编造；
                - 若明细为空，如实说明「该区间没有记录」，不要虚构内容；
                - highlights 与 suggestions 各 2-4 条，每条一句话，不要与正文完全重复。
                """);
        return builder.toString();
    }

    private static String nullSafe(Integer value) {
        return value == null ? "0" : value.toString();
    }

    private static String percent(Double ratio) {
        return ratio == null ? "未知" : Math.round(ratio * 10000.0) / 100.0 + "%";
    }

    /** 秒 → 分钟文本（模型对「秒」的理解极不可靠） */
    private static String minutes(Integer seconds) {
        return seconds == null ? "0" : String.valueOf(Math.round(seconds / 60.0F));
    }

    /** plannedDuration 本身即分钟，不做换算 */
    private static String minutesOfPlan(Integer plannedMinutes) {
        return plannedMinutes == null ? "未定" : plannedMinutes.toString();
    }
}
