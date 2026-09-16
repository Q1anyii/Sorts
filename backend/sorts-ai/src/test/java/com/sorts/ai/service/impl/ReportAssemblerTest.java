package com.sorts.ai.service.impl;

import com.sorts.ai.client.dto.StatisticsSummaryDto;
import com.sorts.ai.dto.AIReportInfo;
import com.sorts.ai.entity.AiReport;
import com.sorts.ai.enums.ReportStatus;
import com.sorts.ai.enums.ReportType;
import com.sorts.ai.tool.support.ToolJsonCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报告装配单元测试：标题兜底、周期标识、附属数组的编解码。
 *
 * @author sorts
 */
class ReportAssemblerTest {

    private final ReportAssembler assembler = new ReportAssembler(new ToolJsonCodec());

    @Test
    @DisplayName("兜底标题：三种类型的命名符合中文习惯")
    void defaultTitles() {
        assertEquals("2026-09-16 日总结", assembler.defaultTitle(ReportType.DAILY, LocalDate.of(2026, 9, 16)));
        assertEquals("2026年9月 月总结", assembler.defaultTitle(ReportType.MONTHLY, LocalDate.of(2026, 9, 1)));
        assertEquals("2026年 年总结", assembler.defaultTitle(ReportType.YEARLY, LocalDate.of(2026, 1, 1)));
    }

    @Test
    @DisplayName("周期标识：日/月/年各取其粒度")
    void periodKeys() {
        assertEquals("2026-09-16", assembler.periodKey(ReportType.DAILY, LocalDate.of(2026, 9, 16)));
        assertEquals("2026-09", assembler.periodKey(ReportType.MONTHLY, LocalDate.of(2026, 9, 1)));
        assertEquals("2026", assembler.periodKey(ReportType.YEARLY, LocalDate.of(2026, 1, 1)));
    }

    @Test
    @DisplayName("模型没给标题时用兜底标题；完成率以统计服务为准")
    void fillsCompleted() {
        AiReport report = new AiReport();
        StatisticsSummaryDto summary = new StatisticsSummaryDto();
        summary.setCompletionRate(0.75);
        summary.setTotalFocusTime(7200);

        ReportComposer.Composed composed = new ReportComposer.Composed(
                null, "正文", List.of("亮点"), List.of("建议"), summary);
        assembler.fillCompleted(report, composed, ReportType.DAILY, LocalDate.of(2026, 9, 16));

        assertEquals("2026-09-16 日总结", report.getTitle());
        assertEquals(ReportStatus.COMPLETED, report.getStatus());
        assertEquals(0.75, report.getCompletionRate(), 0.0001);
        assertEquals(7200, report.getTotalFocusTime());
        assertEquals("[\"亮点\"]", report.getHighlights());
    }

    @Test
    @DisplayName("统计缺失：不写完成率，但报告仍然完成")
    void fillsWithoutSummary() {
        AiReport report = new AiReport();
        assembler.fillCompleted(report, new ReportComposer.Composed("标题", "正文", List.of(), List.of(), null),
                ReportType.YEARLY, LocalDate.of(2026, 1, 1));

        assertEquals(ReportStatus.COMPLETED, report.getStatus());
        assertEquals(null, report.getCompletionRate());
        assertEquals("[]", report.getHighlights());
    }

    @Test
    @DisplayName("超长标题按列宽截断，避免插入报错")
    void truncatesTitle() {
        AiReport report = new AiReport();
        String longTitle = "标".repeat(200);
        assembler.fillCompleted(report, new ReportComposer.Composed(longTitle, "正文", List.of(), List.of(), null),
                ReportType.DAILY, LocalDate.of(2026, 9, 16));

        assertEquals(128, report.getTitle().length());
    }

    @Test
    @DisplayName("附属数组：正常解析；损坏时降级为空列表而不是让接口整体失败")
    void parsesTexts() {
        assertEquals(List.of("a", "b"), assembler.parseTexts("[\"a\",\"b\"]"));
        assertEquals(List.of(), assembler.parseTexts("{坏数据"));
        assertEquals(List.of(), assembler.parseTexts(null));
    }

    @Test
    @DisplayName("实体转 DTO：字段完整且含解析后的数组")
    void toInfo() {
        AiReport report = new AiReport();
        report.setId(1L);
        report.setUserId(7L);
        report.setType(ReportType.MONTHLY.name());
        report.setTitle("标题");
        report.setContent("正文");
        report.setHighlights("[\"亮点\"]");
        report.setSuggestions("[\"建议\"]");
        report.setStatus(ReportStatus.GENERATING);

        AIReportInfo info = assembler.toInfo(report);

        assertEquals(1L, info.getId());
        assertEquals("MONTHLY", info.getType());
        assertEquals(List.of("亮点"), info.getHighlights());
        assertEquals(ReportStatus.GENERATING, info.getStatus());
        assertTrue(info.getSuggestions().contains("建议"));
    }
}
