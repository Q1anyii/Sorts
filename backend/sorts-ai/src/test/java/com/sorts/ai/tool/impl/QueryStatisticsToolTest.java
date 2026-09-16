package com.sorts.ai.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sorts.ai.client.ScheduleClient;
import com.sorts.ai.client.dto.StatisticsSummaryDto;
import com.sorts.ai.client.dto.TagStatDto;
import com.sorts.ai.client.dto.TrendPointDto;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * 查询统计工具单元测试：秒转分钟、比率转百分比、长趋势截断。
 *
 * <p>这层换算看似琐碎，却是「AI 一本正经说错数」的主要来源，必须用测试钉住。</p>
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
class QueryStatisticsToolTest {

    private static final Long USER_ID = 7L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ToolJsonCodec jsonCodec = new ToolJsonCodec();

    @Mock
    private ScheduleClient scheduleClient;

    private QueryStatisticsTool tool;

    @BeforeEach
    void setUp() {
        tool = new QueryStatisticsTool(jsonCodec, scheduleClient);
    }

    @Test
    @DisplayName("时长秒转分钟、比率转百分比，避免模型误读单位")
    void convertsUnits() throws Exception {
        StatisticsSummaryDto summary = new StatisticsSummaryDto();
        summary.setPeriod("day");
        summary.setTotalSchedules(5);
        summary.setCompletedSchedules(3);
        summary.setCompletionRate(0.6);
        summary.setTotalFocusTime(7200);
        summary.setAvgFocusTime(3600);
        summary.setTagDistribution(List.of(tag("学习", 2, 6000, 0.8333)));

        when(scheduleClient.summary(eq(USER_ID), eq("day"), isNull())).thenReturn(Result.success(summary));

        JsonNode data = objectMapper.readTree(tool.execute(USER_ID, objectMapper.readTree("{}")));

        assertEquals(120, data.get("totalFocusMinutes").asInt());
        assertEquals(60, data.get("avgFocusMinutes").asInt());
        assertEquals(60.0, data.get("completionRatePercent").asDouble(), 0.001);
        JsonNode tag = data.get("tagDistribution").get(0);
        assertEquals(100, tag.get("focusMinutes").asInt());
        assertEquals(83.33, tag.get("percentagePercent").asDouble(), 0.001);
    }

    @Test
    @DisplayName("period 缺省为 day，并把用户给的锚点日期透传给下游")
    void defaultsToDayAndPassesDate() throws Exception {
        StatisticsSummaryDto summary = new StatisticsSummaryDto();
        summary.setPeriod("day");
        when(scheduleClient.summary(eq(USER_ID), eq("day"), any(LocalDate.class)))
                .thenReturn(Result.success(summary));

        tool.execute(USER_ID, objectMapper.readTree("{\"date\":\"2026/09/16\"}"));

        org.mockito.Mockito.verify(scheduleClient)
                .summary(eq(USER_ID), eq("day"), eq(LocalDate.of(2026, 9, 16)));
    }

    @Test
    @DisplayName("趋势点超长时只保留最近 30 个，防止年度统计挤爆上下文")
    void truncatesLongTrend() throws Exception {
        List<TrendPointDto> trend = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            TrendPointDto point = new TrendPointDto();
            point.setDate(LocalDate.of(2026, 1, 1).plusDays(i));
            point.setTotal(1);
            point.setCompleted(1);
            point.setTotalDuration(600);
            trend.add(point);
        }
        StatisticsSummaryDto summary = new StatisticsSummaryDto();
        summary.setPeriod("year");
        summary.setDailyTrend(trend);

        when(scheduleClient.summary(eq(USER_ID), eq("year"), isNull())).thenReturn(Result.success(summary));

        JsonNode data = objectMapper.readTree(tool.execute(USER_ID, objectMapper.readTree("{\"period\":\"year\"}")));

        assertEquals(30, data.get("dailyTrend").size());
        assertTrue(data.get("dailyTrend").get(0).get("date").asText().compareTo("2026-01-01") > 0);
    }

    private TagStatDto tag(String name, int count, int totalDuration, double percentage) {
        TagStatDto tag = new TagStatDto();
        tag.setTag(name);
        tag.setCount(count);
        tag.setTotalDuration(totalDuration);
        tag.setPercentage(percentage);
        return tag;
    }
}
