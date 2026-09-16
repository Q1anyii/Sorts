package com.sorts.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sorts.ai.client.ScheduleClient;
import com.sorts.ai.client.dto.StatisticsSummaryDto;
import com.sorts.ai.client.dto.TagStatDto;
import com.sorts.ai.client.dto.TrendPointDto;
import com.sorts.ai.tool.ToolLevel;
import com.sorts.ai.tool.support.AbstractAiTool;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.ai.tool.support.DateTimes;
import com.sorts.ai.tool.support.JsonArgs;
import com.sorts.ai.tool.support.Results;
import com.sorts.ai.tool.support.Schemas;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询统计汇总。
 *
 * <p>「秒转分钟」在这里做，而不是把原始秒数回灌给模型：模型拿到 7200 后经常直接说成
 * 「7200 分钟」，因为它不理解字段单位。边界处做一次换算，比在提示词里反复叮嘱可靠。</p>
 *
 * @author sorts
 */
@Component
public class QueryStatisticsTool extends AbstractAiTool {

    /** 趋势点上限：避免年度统计把一长串按天数据塞进上下文 */
    private static final int MAX_TREND_POINTS = 30;

    private final ScheduleClient scheduleClient;

    public QueryStatisticsTool(ToolJsonCodec jsonCodec, ScheduleClient scheduleClient) {
        super(jsonCodec);
        this.scheduleClient = scheduleClient;
    }

    @Override
    public String name() {
        return "queryStatistics";
    }

    @Override
    public String description() {
        return "查询当前用户的日程统计汇总：完成率、日程数、专注时长、标签时间分布与按天趋势。"
                + "当用户问「我这周效率怎么样」「我最近时间都花在哪了」「今天专注了多久」时调用。";
    }

    @Override
    public ToolLevel level() {
        return ToolLevel.READ;
    }

    @Override
    public Map<String, Object> parameters() {
        return Schemas.object(Schemas.props(
                "period", Schemas.enumString(Schemas.list("day", "week", "month", "year"),
                        "统计周期，缺省 day"),
                "date", Schemas.string("统计锚点日期，格式 yyyy-MM-dd，缺省今天；按天统计时指定某一天")
        ));
    }

    @Override
    public String execute(Long userId, JsonNode args) {
        String period = JsonArgs.text(args, "period");
        if (period == null) {
            period = "day";
        }
        String dateText = JsonArgs.text(args, "date");
        LocalDate date = dateText == null ? null : DateTimes.parseDate(dateText, "date");

        StatisticsSummaryDto summary = Results.unwrap("查询统计汇总", scheduleClient.summary(userId, period, date));

        Map<String, Object> data = result();
        data.put("period", summary.getPeriod() == null ? period : summary.getPeriod());
        data.put("startDate", summary.getStartDate());
        data.put("endDate", summary.getEndDate());
        data.put("totalSchedules", summary.getTotalSchedules());
        data.put("completedSchedules", summary.getCompletedSchedules());
        data.put("completionRatePercent", toPercent(summary.getCompletionRate()));
        data.put("totalFocusMinutes", toMinutes(summary.getTotalFocusTime()));
        data.put("avgFocusMinutes", toMinutes(summary.getAvgFocusTime()));
        data.put("tagDistribution", tags(summary.getTagDistribution()));
        data.put("dailyTrend", trend(summary.getDailyTrend()));
        return json(data);
    }

    private List<Map<String, Object>> tags(List<TagStatDto> distribution) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (distribution == null) {
            return items;
        }
        for (TagStatDto tag : distribution) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tag", tag.getTag());
            item.put("count", tag.getCount());
            item.put("focusMinutes", toMinutes(tag.getTotalDuration()));
            item.put("percentagePercent", toPercent(tag.getPercentage()));
            items.add(item);
        }
        return items;
    }

    private List<Map<String, Object>> trend(List<TrendPointDto> points) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (points == null || points.isEmpty()) {
            return items;
        }
        int from = Math.max(0, points.size() - MAX_TREND_POINTS);
        for (TrendPointDto point : points.subList(from, points.size())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", point.getDate());
            item.put("total", point.getTotal());
            item.put("completed", point.getCompleted());
            item.put("focusMinutes", toMinutes(point.getTotalDuration()));
            items.add(item);
        }
        return items;
    }

    /** 秒 → 分钟（四舍五入到整数），空值保持 null 让模型知道「没有数据」而不是「为零」 */
    private Integer toMinutes(Integer seconds) {
        return seconds == null ? null : Math.round(seconds / 60.0F);
    }

    private Double toPercent(Double ratio) {
        return ratio == null ? null : Math.round(ratio * 10000.0) / 100.0;
    }
}
