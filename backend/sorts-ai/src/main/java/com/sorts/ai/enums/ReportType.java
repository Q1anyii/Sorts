package com.sorts.ai.enums;

/**
 * 报告类型（对应 api-spec 的 AIReportInfo.type）。
 *
 * <p>WEEKLY 暂未开放生成入口（api-spec 也没有周报接口），但枚举保留，
 * 避免后续加周报时改动存储值语义。</p>
 *
 * @author sorts
 */
public enum ReportType {

    DAILY("日总结"),
    WEEKLY("周总结"),
    MONTHLY("月总结"),
    YEARLY("年总结");

    private final String label;

    ReportType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        for (ReportType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}
