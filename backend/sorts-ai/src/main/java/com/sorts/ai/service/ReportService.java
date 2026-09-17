package com.sorts.ai.service;

import com.sorts.ai.dto.AIReportInfo;
import com.sorts.ai.dto.AISummaryRequest;
import com.sorts.ai.dto.AsyncReportResponse;
import com.sorts.ai.dto.PeriodSummaryRequest;
import com.sorts.common.result.PageData;

import java.util.function.Consumer;

/**
 * 周期总结报告：日 / 月 / 年。
 *
 * <p>日报同步生成（数据量小、用户在意即时反馈），月报与年报异步生成
 * （模型耗时长，同步必然超时）。</p>
 *
 * @author sorts
 */
public interface ReportService {

    /**
     * 生成每日总结。
     *
     * @param onDelta 正文增量回调（SSE 打字机效果），可为 null
     */
    AIReportInfo generateDaily(Long userId, AISummaryRequest request, Consumer<String> onDelta);

    /** 提交月度总结生成任务，立即返回 reportId */
    AsyncReportResponse generateMonthly(Long userId, PeriodSummaryRequest request);

    /** 提交年度总结生成任务，立即返回 reportId */
    AsyncReportResponse generateYearly(Long userId, PeriodSummaryRequest request);

    /** 分页查询历史报告 */
    PageData<AIReportInfo> list(Long userId, String type, int page, int pageSize);

    /** 报告详情 */
    AIReportInfo get(Long userId, Long reportId);

    /** 逻辑删除指定织史 */
    void delete(Long userId, Long reportId);

    /** 批量逻辑删除织史：事务内全部成功或全部失败 */
    void deleteBatch(Long userId, java.util.List<Long> reportIds);
}
