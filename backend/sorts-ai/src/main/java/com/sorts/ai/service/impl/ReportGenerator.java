package com.sorts.ai.service.impl;

import com.sorts.ai.config.AsyncConfig;
import com.sorts.ai.entity.AiReport;
import com.sorts.ai.enums.ReportStatus;
import com.sorts.ai.enums.ReportType;
import com.sorts.ai.mapper.AiReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 异步报告生成器。
 *
 * <p>刻意独立成一个 Bean：{@code @Async} 依赖 Spring 代理，同一个类内部自调用
 * 会绕过代理、退化成同步执行——「明明加了 @Async 却仍然阻塞」是最常见的坑，
 * 拆成独立 Bean 从结构上就不给它发生的机会。</p>
 *
 * <p>注意这里<b>不抛异常</b>：后台任务的异常没人接，只会在日志里留下一行堆栈，
 * 而数据库里的记录会永远停在 GENERATING。因此统一捕获并落 FAILED + 原因。</p>
 *
 * @author sorts
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGenerator {

    private static final int ERROR_MAX = 500;

    private final ReportComposer composer;

    private final ReportAssembler assembler;

    private final AiReportMapper reportMapper;

    @Async(AsyncConfig.AI_EXECUTOR)
    public void generate(Long reportId, Long userId, String typeLabel, String period, String reportType,
                         LocalDate start, LocalDate end) {
        log.info("开始异步生成报告 reportId={} type={} {} ~ {}", reportId, reportType, start, end);
        try {
            ReportComposer.Composed composed = composer.compose(userId, period, typeLabel, start, end, false, null);

            AiReport update = new AiReport();
            update.setId(reportId);
            assembler.fillCompleted(update, composed, ReportType.valueOf(reportType), start);
            reportMapper.updateById(update);

            log.info("报告生成完成 reportId={}", reportId);
        } catch (Exception e) {
            log.error("报告生成失败 reportId={}", reportId, e);
            markFailed(reportId, e.getMessage());
        }
    }

    private void markFailed(Long reportId, String message) {
        try {
            AiReport failed = new AiReport();
            failed.setId(reportId);
            failed.setStatus(ReportStatus.FAILED);
            failed.setErrorMsg(truncate(message));
            reportMapper.updateById(failed);
        } catch (Exception e) {
            // 连失败状态都写不进去（数据库不可用）：只能记日志，避免异常逃逸到线程池
            log.error("写入报告失败状态时再次出错 reportId={}", reportId, e);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "未知错误";
        }
        return message.length() <= ERROR_MAX ? message : message.substring(0, ERROR_MAX);
    }
}
