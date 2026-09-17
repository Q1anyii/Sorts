package com.sorts.ai.service;

import com.sorts.ai.service.dto.FileParseResult;

/**
 * 文件解析服务：把用户上传的 md / docx / doc / txt 解析为纯文本。
 *
 * <p>解析结果只作为 AI 对话的「临时记忆拼接」，不落库、不持久化。</p>
 *
 * @author sorts
 */
public interface FileParseService {

    /**
     * 解析文件内容为纯文本。
     *
     * @param originalFilename 原始文件名（用于识别扩展名）
     * @param bytes            文件字节
     * @return 解析结果（文件名 + 字符数 + 文本）
     */
    FileParseResult parse(String originalFilename, byte[] bytes);
}
