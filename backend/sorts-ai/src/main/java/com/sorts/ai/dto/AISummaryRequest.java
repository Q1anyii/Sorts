package com.sorts.ai.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 总结生成请求（对应 api-spec.json 的 AISummaryRequest）。
 *
 * @author sorts
 */
@Data
public class AISummaryRequest {

    /** 指定日期，缺省今天 */
    private LocalDate date;

    /** 是否流式返回，缺省 true */
    private Boolean stream = Boolean.TRUE;

    public boolean streaming() {
        return stream == null || stream;
    }
}
