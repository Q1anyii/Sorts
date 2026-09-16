package com.sorts.ai.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import com.sorts.ai.service.ReportService;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.common.result.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Consumer;

/**
 * 报告服务实现。
 *
 * <p>同步/异步的划分依据不是「实现简单」，而是「用户能不能等」：
 * 日报数据量小、用户点完就想看，必须同步且支持流式；
 * 月/年报要跑的区间大、模型耗时长，同步必然超时，因此落 GENERATING 后立即返回 reportId。</p>
 *
 * @author sorts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    /** 单页上限：报告正文较大，一次拉太多会明显拖慢接口 */
    private static final int MAX_PAGE_SIZE = 50;

    /** 异步任务预计耗时（秒），仅用于前端展示进度提示 */
    private static final int ESTIMATED_SECONDS = 20;

    private final DeepSeekProperties properties;

    private final ReportComposer composer;

    private final ReportAssembler assembler;

    private final ReportGenerator generator;

    private final AiReportMapper reportMapper;

    @Override
    public AIReportInfo generateDaily(Long userId, AISummaryRequest request, Consumer<String> onDelta) {
        requireConfigured();
        LocalDate date = request == null || request.getDate() == null ? LocalDate.now() : request.getDate();

        ReportComposer.Composed composed = composer.compose(
                userId, "day", ReportType.DAILY.label(), date, date, true, onDelta);

        AiReport report = new AiReport();
        report.setUserId(userId);
        report.setType(ReportType.DAILY.name());
        report.setPeriodKey(assembler.periodKey(ReportType.DAILY, date));
        assembler.fillCompleted(report, composed, ReportType.DAILY, date);
        reportMapper.insert(report);

        return assembler.toInfo(report);
    }

    @Override
    public AsyncReportResponse generateMonthly(Long userId, PeriodSummaryRequest request) {
        requireConfigured();
        LocalDate today = LocalDate.now();
        int year = request == null || request.getYear() == null ? today.getYear() : request.getYear();
        int month = request == null || request.getMonth() == null ? today.getMonthValue() : request.getMonth();
        if (month < 1 || month > 12) {
            throw new BizException(ErrorCode.PARAM_ERROR, "月份必须在 1-12 之间");
        }

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        return submit(userId, ReportType.MONTHLY, start, end);
    }

    @Override
    public AsyncReportResponse generateYearly(Long userId, PeriodSummaryRequest request) {
        requireConfigured();
        int year = request == null || request.getYear() == null ? LocalDate.now().getYear() : request.getYear();
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        return submit(userId, ReportType.YEARLY, start, end);
    }

    @Override
    public PageData<AIReportInfo> list(Long userId, String type, int page, int pageSize) {
        String normalizedType = null;
        if (StringUtils.hasText(type)) {
            if (!ReportType.isValid(type)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "报告类型不合法：" + type);
            }
            normalizedType = type.strip().toUpperCase();
        }
        long current = Math.max(page, 1);
        long size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        Page<AiReport> result = reportMapper.selectPage(new Page<>(current, size),
                Wrappers.<AiReport>lambdaQuery()
                        .eq(AiReport::getUserId, userId)
                        .eq(normalizedType != null, AiReport::getType, normalizedType)
                        .orderByDesc(AiReport::getCreatedAt));

        List<AIReportInfo> records = result.getRecords().stream().map(assembler::toInfo).toList();
        return PageData.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AIReportInfo get(Long userId, Long reportId) {
        AiReport report = reportMapper.selectOne(Wrappers.<AiReport>lambdaQuery()
                .eq(AiReport::getId, reportId)
                .eq(AiReport::getUserId, userId));
        if (report == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "报告不存在");
        }
        return assembler.toInfo(report);
    }

    /** 落一条 GENERATING 记录 → 提交异步任务 → 立即返回 reportId */
    private AsyncReportResponse submit(Long userId, ReportType type, LocalDate start, LocalDate end) {
        AiReport report = new AiReport();
        report.setUserId(userId);
        report.setType(type.name());
        report.setPeriodKey(assembler.periodKey(type, start));
        report.setTitle(assembler.defaultTitle(type, start));
        report.setStatus(ReportStatus.GENERATING);
        reportMapper.insert(report);

        // 必须用落库后的自增 ID 驱动异步任务：先返回再生成，用户拿到的是可轮询的凭据
        generator.generate(report.getId(), userId, type.label(), periodOf(type), type.name(), start, end);

        return AsyncReportResponse.builder()
                .reportId(report.getId())
                .status(ReportStatus.GENERATING)
                .estimatedSeconds(ESTIMATED_SECONDS)
                .build();
    }

    private String periodOf(ReportType type) {
        return switch (type) {
            case DAILY, WEEKLY -> "day";
            case MONTHLY -> "month";
            case YEARLY -> "year";
        };
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "梭灵尚未配置模型密钥：请设置环境变量 DEEPSEEK_API_KEY 后重启 sorts-ai");
        }
    }
}
