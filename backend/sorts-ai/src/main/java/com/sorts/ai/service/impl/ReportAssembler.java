package com.sorts.ai.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sorts.ai.client.dto.StatisticsSummaryDto;
import com.sorts.ai.dto.AIReportInfo;
import com.sorts.ai.entity.AiReport;
import com.sorts.ai.enums.ReportStatus;
import com.sorts.ai.enums.ReportType;
import com.sorts.ai.tool.support.ToolJsonCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * 报告实体与 DTO 的装配。
 *
 * <p>集中一处是为了让「日报」与「月/年报」产出的字段口径完全一致——
 * 两边各写一份映射，迟早会出现日报有 highlights、月报为空这种不一致。</p>
 *
 * @author sorts
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportAssembler {

    private final ToolJsonCodec jsonCodec;

    /** 兜底标题：模型没给 title 时也要有个像样的名字 */
    public String defaultTitle(ReportType type, LocalDate start) {
        return switch (type) {
            case DAILY -> start + " " + type.label();
            case MONTHLY -> YearMonth.from(start).getYear() + "年" + YearMonth.from(start).getMonthValue()
                    + "月 " + type.label();
            case YEARLY -> start.getYear() + "年 " + type.label();
            case WEEKLY -> start + " 起 " + type.label();
        };
    }

    /** 周期标识：用于同周期去重与展示 */
    public String periodKey(ReportType type, LocalDate start) {
        return switch (type) {
            case DAILY, WEEKLY -> start.toString();
            case MONTHLY -> String.format("%04d-%02d", start.getYear(), start.getMonthValue());
            case YEARLY -> String.valueOf(start.getYear());
        };
    }

    /** 把撰写结果与统计数据落到实体上，并置为已完成 */
    public void fillCompleted(AiReport target, ReportComposer.Composed composed, ReportType type, LocalDate start) {
        String title = StringUtils.hasText(composed.title()) ? composed.title() : defaultTitle(type, start);
        target.setTitle(truncate(title, 128));
        target.setContent(composed.content());
        target.setHighlights(jsonCodec.write(orEmpty(composed.highlights())));
        target.setSuggestions(jsonCodec.write(orEmpty(composed.suggestions())));
        applySummary(target, composed.summary());
        target.setStatus(ReportStatus.COMPLETED);
        target.setGeneratedAt(LocalDateTime.now());
    }

    /** 完成率与总时长以统计服务为准，而不是采信模型的复述 */
    public void applySummary(AiReport target, StatisticsSummaryDto summary) {
        if (summary == null) {
            return;
        }
        target.setCompletionRate(summary.getCompletionRate());
        target.setTotalFocusTime(summary.getTotalFocusTime());
    }

    public AIReportInfo toInfo(AiReport entity) {
        return AIReportInfo.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .type(entity.getType())
                .title(entity.getTitle())
                .content(entity.getContent())
                .completionRate(entity.getCompletionRate())
                .totalFocusTime(entity.getTotalFocusTime())
                .highlights(parseTexts(entity.getHighlights()))
                .suggestions(parseTexts(entity.getSuggestions()))
                .generatedAt(entity.getGeneratedAt())
                .status(entity.getStatus())
                .build();
    }

    /** 解析存库的 JSON 字符串数组；损坏时返回空列表而不是让接口整体失败 */
    public List<String> parseTexts(String stored) {
        if (!StringUtils.hasText(stored)) {
            return List.of();
        }
        try {
            return jsonCodec.mapper().readValue(stored, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("报告附属字段解析失败，按空处理：{}", e.getMessage());
            return List.of();
        }
    }

    private List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
