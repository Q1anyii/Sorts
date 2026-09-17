package com.sorts.ai.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件解析结果。
 *
 * @author sorts
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileParseResult {

    /** 原始文件名 */
    private String fileName;

    /** 解析出的字符数 */
    private int chars;

    /** 解析出的纯文本（截断到上限内） */
    private String text;
}
