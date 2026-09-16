package com.sorts.ai.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sorts.ai.config.DeepSeekProperties;
import com.sorts.ai.dto.AIReportInfo;
import com.sorts.ai.dto.AISummaryRequest;
import com.sorts.ai.dto.AsyncReportResponse;
import com.sorts.ai.dto.PeriodSummaryRequest;
import com.sorts.ai.entity.AiReport;
import com.sorts.ai.enums.ReportStatus;
import com.sorts.ai.enums.ReportType;
import com.sorts.ai.mapper.AiReportMapper;
import com.sorts.ai.tool.support.ToolJsonCodec;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.PageData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 报告服务单元测试：同步日报、异步月/年报受理、分页与详情、类型校验。
 *
 * @author sorts
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceImplTest {

    private static final Long USER_ID = 7L;

    private final ToolJsonCodec jsonCodec = new ToolJsonCodec();

    private final ReportAssembler assembler = new ReportAssembler(jsonCodec);

    @Mock
    private ReportComposer composer;

    @Mock
    private ReportGenerator generator;

    @Mock
    private AiReportMapper reportMapper;

    private DeepSeekProperties properties;

    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new DeepSeekProperties();
        properties.setApiKey("test-key");
        service = new ReportServiceImpl(properties, composer, assembler, generator, reportMapper);
        when(reportMapper.insert(any(AiReport.class))).thenAnswer(invocation -> {
            AiReport report = invocation.getArgument(0);
            report.setId(1L);
            return 1;
        });
    }

    @Test
    @DisplayName("日报：同步生成并落库 COMPLETED，返回可直接展示的信息")
    void generateDailyPersists() {
        when(composer.compose(eq(USER_ID), eq("day"), eq(ReportType.DAILY.label()),
                any(LocalDate.class), any(LocalDate.class), eq(true), any()))
                .thenReturn(composed("高效的一天", "正文内容", List.of("上午专注度高"), List.of("下午可减少切换")));

        AIReportInfo info = service.generateDaily(USER_ID, new AISummaryRequest(), null);

        assertEquals(1L, info.getId());
        assertEquals(ReportType.DAILY.name(), info.getType());
        assertEquals("高效的一天", info.getTitle());
        assertEquals("正文内容", info.getContent());
        assertEquals(List.of("上午专注度高"), info.getHighlights());
        assertEquals(ReportStatus.COMPLETED, info.getStatus());

        org.mockito.ArgumentCaptor<AiReport> captor = org.mockito.ArgumentCaptor.forClass(AiReport.class);
        verify(reportMapper).insert(captor.capture());
        assertEquals(LocalDate.now().toString(), captor.getValue().getPeriodKey());
        assertEquals(USER_ID, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("日报：统计服务降级时仍能生成（不因缺统计而整个失败）")
    void dailyWithoutSummaryStillWorks() {
        when(composer.compose(any(), anyString(), anyString(), any(), any(), eq(true), any()))
                .thenReturn(new ReportComposer.Composed("标题", "正文", List.of(), List.of(), null));

        AIReportInfo info = service.generateDaily(USER_ID, new AISummaryRequest(), null);

        assertEquals(ReportStatus.COMPLETED, info.getStatus());
        assertEquals(null, info.getCompletionRate());
    }

    @Test
    @DisplayName("月报：先落 GENERATING 再提交异步，立即返回 reportId")
    void monthlySubmitsAsync() {
        PeriodSummaryRequest request = new PeriodSummaryRequest();
        request.setYear(2026);
        request.setMonth(9);

        AsyncReportResponse response = service.generateMonthly(USER_ID, request);

        assertEquals(1L, response.getReportId());
        assertEquals(ReportStatus.GENERATING, response.getStatus());
        assertEquals(20, response.getEstimatedSeconds());

        verify(generator).generate(eq(1L), eq(USER_ID), eq(ReportType.MONTHLY.label()), eq("month"),
                eq(ReportType.MONTHLY.name()), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)));
    }

    @Test
    @DisplayName("年报：区间锁定自然年首尾")
    void yearlyUsesFullYear() {
        PeriodSummaryRequest request = new PeriodSummaryRequest();
        request.setYear(2026);

        service.generateYearly(USER_ID, request);

        verify(generator).generate(eq(1L), eq(USER_ID), eq(ReportType.YEARLY.label()), eq("year"),
                eq(ReportType.YEARLY.name()), eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 12, 31)));
    }

    @Test
    @DisplayName("月份越界：参数错误，且不产生垃圾记录")
    void rejectsInvalidMonth() {
        PeriodSummaryRequest request = new PeriodSummaryRequest();
        request.setYear(2026);
        request.setMonth(13);

        BizException error = assertThrows(BizException.class, () -> service.generateMonthly(USER_ID, request));

        assertEquals(400, error.getCode());
        verify(reportMapper, never()).insert(any(AiReport.class));
    }

    @Test
    @DisplayName("列表：分页字段与附属数组正确映射")
    void listMapsPage() {
        AiReport entity = new AiReport();
        entity.setId(9L);
        entity.setUserId(USER_ID);
        entity.setType(ReportType.DAILY.name());
        entity.setTitle("标题");
        entity.setHighlights("[\"亮点\"]");
        entity.setSuggestions("[\"建议\"]");
        entity.setGeneratedAt(LocalDateTime.of(2026, 9, 16, 22, 0));

        Page<AiReport> page = new Page<>(1, 10);
        page.setRecords(List.of(entity));
        page.setTotal(1);
        when(reportMapper.selectPage(ArgumentMatchers.<Page<AiReport>>any(), any())).thenReturn(page);

        PageData<AIReportInfo> result = service.list(USER_ID, "daily", 1, 10);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(10, result.getPageSize());
        AIReportInfo info = result.getList().get(0);
        assertEquals(List.of("亮点"), info.getHighlights());
        assertEquals(List.of("建议"), info.getSuggestions());
    }

    @Test
    @DisplayName("列表：类型非法直接拒绝，避免全表扫描")
    void listRejectsInvalidType() {
        BizException error = assertThrows(BizException.class, () -> service.list(USER_ID, "HOURLY", 1, 10));
        assertEquals(400, error.getCode());
        verify(reportMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("列表：pageSize 超上限被收敛")
    void listCapsPageSize() {
        Page<AiReport> page = new Page<>(1, 50);
        page.setRecords(List.of());
        page.setTotal(0);
        when(reportMapper.selectPage(ArgumentMatchers.<Page<AiReport>>any(), any())).thenReturn(page);

        service.list(USER_ID, null, 0, 999);

        verify(reportMapper).selectPage(ArgumentMatchers.argThat(p -> p.getSize() == 50 && p.getCurrent() == 1), any());
    }

    @Test
    @DisplayName("详情：不存在时返回 404")
    void getMissing() {
        when(reportMapper.selectOne(any())).thenReturn(null);

        BizException error = assertThrows(BizException.class, () -> service.get(USER_ID, 404L));
        assertEquals(404, error.getCode());
        assertTrue(error.getMessage().contains("报告不存在"));
    }

    @Test
    @DisplayName("未配置密钥：日报同步路径直接 503")
    void dailyRequiresApiKey() {
        properties.setApiKey("");
        BizException error = assertThrows(BizException.class,
                () -> service.generateDaily(USER_ID, new AISummaryRequest(), null));
        assertEquals(503, error.getCode());
        verify(composer, never()).compose(any(), anyString(), anyString(), any(), any(), eq(true), any());
    }

    // ---------------------------------------------------------------- 同周期幂等

    @Test
    @DisplayName("月报：同周期已生成完成 → 直接复用，不再调模型也不新增记录")
    void monthlyReusesCompletedReport() {
        AiReport existing = existing(2026, 9, ReportType.MONTHLY, "2026-09", ReportStatus.COMPLETED);
        when(reportMapper.selectOne(any())).thenReturn(existing);

        PeriodSummaryRequest request = new PeriodSummaryRequest();
        request.setYear(2026);
        request.setMonth(9);

        AsyncReportResponse response = service.generateMonthly(USER_ID, request);

        assertEquals(99L, response.getReportId());
        assertEquals(ReportStatus.COMPLETED, response.getStatus());
        assertEquals(0, response.getEstimatedSeconds());
        assertTrue(response.isReused(), "命中同周期已完成报告时应标记复用");
        verify(reportMapper, never()).insert(any(AiReport.class));
        verify(generator, never()).generate(any(), any(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("月报：同周期仍在生成中 → 复用同一个 reportId 继续轮询，不重复提交任务")
    void monthlyReusesGeneratingReport() {
        AiReport existing = existing(2026, 9, ReportType.MONTHLY, "2026-09", ReportStatus.GENERATING);
        when(reportMapper.selectOne(any())).thenReturn(existing);

        PeriodSummaryRequest request = new PeriodSummaryRequest();
        request.setYear(2026);
        request.setMonth(9);

        AsyncReportResponse response = service.generateMonthly(USER_ID, request);

        assertEquals(99L, response.getReportId());
        assertEquals(ReportStatus.GENERATING, response.getStatus());
        assertTrue(response.isReused());
        verify(reportMapper, never()).insert(any(AiReport.class));
        verify(generator, never()).generate(any(), any(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("月报：同周期上一条失败 → 清掉旧记录后重新生成")
    void monthlyRegeneratesAfterFailure() {
        AiReport failed = existing(2026, 9, ReportType.MONTHLY, "2026-09", ReportStatus.FAILED);
        when(reportMapper.selectOne(any())).thenReturn(failed);

        PeriodSummaryRequest request = new PeriodSummaryRequest();
        request.setYear(2026);
        request.setMonth(9);

        AsyncReportResponse response = service.generateMonthly(USER_ID, request);

        assertEquals(ReportStatus.GENERATING, response.getStatus());
        assertEquals(false, response.isReused());
        verify(reportMapper).deleteById(99L);
        verify(reportMapper).insert(any(AiReport.class));
        verify(generator).generate(eq(1L), eq(USER_ID), eq(ReportType.MONTHLY.label()), eq("month"),
                eq(ReportType.MONTHLY.name()), eq(LocalDate.of(2026, 9, 1)), eq(LocalDate.of(2026, 9, 30)));
    }

    @Test
    @DisplayName("月报：force=true 时覆盖刷新，重新调模型")
    void monthlyForceRegenerates() {
        AiReport existing = existing(2026, 9, ReportType.MONTHLY, "2026-09", ReportStatus.COMPLETED);
        when(reportMapper.selectOne(any())).thenReturn(existing);

        PeriodSummaryRequest request = new PeriodSummaryRequest();
        request.setYear(2026);
        request.setMonth(9);
        request.setForce(true);

        AsyncReportResponse response = service.generateMonthly(USER_ID, request);

        assertEquals(ReportStatus.GENERATING, response.getStatus());
        assertEquals(false, response.isReused());
        verify(reportMapper).deleteById(99L);
        verify(generator).generate(any(), any(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("年报：同周期已存在 → 同样按幂等处理")
    void yearlyIsIdempotent() {
        AiReport existing = existing(2026, null, ReportType.YEARLY, "2026", ReportStatus.COMPLETED);
        when(reportMapper.selectOne(any())).thenReturn(existing);

        PeriodSummaryRequest request = new PeriodSummaryRequest();
        request.setYear(2026);

        AsyncReportResponse response = service.generateYearly(USER_ID, request);

        assertEquals(99L, response.getReportId());
        assertTrue(response.isReused());
        verify(reportMapper, never()).insert(any(AiReport.class));
    }

    /** 构造一条「已存在」的报告记录（id 固定 99，便于断言复用） */
    private AiReport existing(int year, Integer month, ReportType type, String periodKey, String status) {
        AiReport report = new AiReport();
        report.setId(99L);
        report.setUserId(USER_ID);
        report.setType(type.name());
        report.setPeriodKey(periodKey);
        report.setTitle(type.label() + year + (month == null ? "" : "-" + month));
        report.setStatus(status);
        report.setCreatedAt(LocalDateTime.now());
        return report;
    }

    private ReportComposer.Composed composed(String title, String content, List<String> highlights,
                                             List<String> suggestions) {
        return new ReportComposer.Composed(title, content, highlights, suggestions, null);
    }
}
