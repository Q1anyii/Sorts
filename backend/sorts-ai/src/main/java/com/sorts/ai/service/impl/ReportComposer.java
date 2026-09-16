package com.sorts.ai.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.sorts.ai.client.ScheduleClient;
import com.sorts.ai.client.dto.ScheduleDto;
import com.sorts.ai.client.dto.ScheduleQueryDto;
import com.sorts.ai.client.dto.StatisticsSummaryDto;
import com.sorts.ai.config.DeepSeekProperties;
import com.sorts.ai.llm.ChatModelClient;
import com.sorts.ai.llm.protocol.ChatCompletionRequest;
import com.sorts.ai.llm.protocol.ChatMessage;
import com.sorts.ai.llm.protocol.LlmResult;
import com.sorts.ai.support.AiPrompts;
import com.sorts.ai.support.JsonPayloads;
import com.sorts.ai.tool.support.Results;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.common.result.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/**
 * 报告「取材 + 撰写」的公共部分。
 *
 * <p>抽出来是因为同步日报与异步月/年报的差别只在「谁来驱动」，
 * 取材口径、提示词、解析规则三者必须完全一致——
 * 若各写一份，日报和月报很快就会对同一件事给出不同说法。</p>
 *
 * @author sorts
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportComposer {

    /** 报告需要一点表达力，但也要稳定：取一个中间温度 */
    private static final double TEMPERATURE = 0.6D;

    /** 日报明细上限：给模型看全天的日程足够了，再多只是烧 token */
    private static final int DETAIL_LIMIT = 50;

    private final ChatModelClient chatModelClient;

    private final DeepSeekProperties properties;

    private final ScheduleClient scheduleClient;

    private final ToolJsonCodec jsonCodec;

    /**
     * 生成报告内容。
     *
     * @param period      统计周期（day/month/year），透传给统计服务
     * @param typeLabel   报告类型中文名，进入提示词
     * @param start       区间起点（也是统计锚点）
     * @param end         区间终点
     * @param withDetails 是否附带日程明细（仅日报需要）
     * @param onDelta     正文增量回调，非空则走流式
     */
    public Composed compose(Long userId, String period, String typeLabel, LocalDate start, LocalDate end,
                            boolean withDetails, Consumer<String> onDelta) {
        StatisticsSummaryDto summary = fetchSummary(userId, period, start);
        List<ScheduleDto> schedules = withDetails ? fetchSchedules(userId, start) : List.of();

        List<ChatMessage> messages = List.of(
                ChatMessage.system(AiPrompts.reportSystem()),
                ChatMessage.user(AiPrompts.reportPrompt(typeLabel, start, end, summary, schedules)));

        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(properties.getModel());
        request.setMessages(messages);
        request.setTemperature(TEMPERATURE);
        request.setMaxTokens(properties.getMaxTokens());

        LlmResult result;
        if (onDelta != null) {
            request.setStream(Boolean.TRUE);
            request.enableStreamUsage();
            result = chatModelClient.stream(request, onDelta);
        } else {
            result = chatModelClient.complete(request);
        }

        Draft draft = parse(result.getContent());
        if (draft == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "梭灵没能生成可用的总结内容，请稍后重试");
        }
        return new Composed(draft.title(), draft.content(), draft.highlights(), draft.suggestions(), summary);
    }

    private StatisticsSummaryDto fetchSummary(Long userId, String period, LocalDate anchor) {
        try {
            return Results.unwrap("查询统计汇总", scheduleClient.summary(userId, period, anchor));
        } catch (BizException e) {
            // 统计服务不可用不阻断报告：明细仍然可以支撑一篇「如实说明数据缺失」的复盘
            log.warn("统计服务不可用，报告将降级为仅依据明细生成：{}", e.getMessage());
            return null;
        }
    }

    private List<ScheduleDto> fetchSchedules(Long userId, LocalDate date) {
        ScheduleQueryDto query = new ScheduleQueryDto();
        query.setView("day");
        query.setDate(date);
        query.setPage(1);
        query.setPageSize(DETAIL_LIMIT);
        try {
            PageData<ScheduleDto> page = Results.unwrap("查询日程列表", scheduleClient.list(userId, query));
            return page == null || page.getList() == null ? List.of() : page.getList();
        } catch (BizException e) {
            log.warn("日程明细拉取失败，报告将仅依据汇总数据：{}", e.getMessage());
            return List.of();
        }
    }

    private Draft parse(String content) {
        String payload = JsonPayloads.extractObject(content);
        if (payload == null) {
            log.warn("总结未返回 JSON 结构，原文前 300 字：{}", abbreviate(content));
            return null;
        }
        try {
            JsonNode root = jsonCodec.readTree(payload);
            String body = root.hasNonNull("content") ? root.get("content").asText() : null;
            if (!StringUtils.hasText(body)) {
                log.warn("总结缺少 content 字段：{}", abbreviate(payload));
                return null;
            }
            String title = root.hasNonNull("title") ? root.get("title").asText() : null;
            return new Draft(title, body, texts(root.get("highlights")), texts(root.get("suggestions")));
        } catch (Exception e) {
            log.warn("总结 JSON 解析失败：{}", abbreviate(payload), e);
            return null;
        }
    }

    private List<String> texts(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return jsonCodec.mapper().convertValue(node, new TypeReference<List<String>>() {
        });
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "(null)";
        }
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    /** 模型原始产出，尚未与统计数据合并 */
    private record Draft(String title, String content, List<String> highlights, List<String> suggestions) {
    }

    /**
     * 撰写结果。
     *
     * @param summary 统计汇总，可能为 null（统计服务降级时）
     */
    public record Composed(String title, String content, List<String> highlights, List<String> suggestions,
                           StatisticsSummaryDto summary) {
    }
}
