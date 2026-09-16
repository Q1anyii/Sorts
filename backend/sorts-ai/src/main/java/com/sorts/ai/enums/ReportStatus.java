package com.sorts.ai.enums;

/**
 * 报告生成状态（对应 api-spec 的 AIReportInfo.status）。
 *
 * @author sorts
 */
public final class ReportStatus {

    /** 生成中（月度/年度走异步，先落这条状态再返回 reportId） */
    public static final String GENERATING = "GENERATING";

    public static final String COMPLETED = "COMPLETED";

    /** 失败：保留记录与原因，便于用户重试与排查，而不是静默消失 */
    public static final String FAILED = "FAILED";

    private ReportStatus() {
    }
}
