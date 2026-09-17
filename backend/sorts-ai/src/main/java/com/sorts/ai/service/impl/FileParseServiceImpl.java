package com.sorts.ai.service.impl;

import com.sorts.ai.service.FileParseService;
import com.sorts.ai.service.dto.FileParseResult;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 文件解析实现。
 *
 * <p>支持 md / txt（UTF-8 直接读取）、docx（OOXML 段落 + 表格）、doc（旧版二进制）。
 * 单文件大小上限 2MB，解析文本超过 20000 字符时截断（与 {@code ChatRequest.fileContent} 上限一致）。</p>
 *
 * @author sorts
 */
@Slf4j
@Service
public class FileParseServiceImpl implements FileParseService {

    /** 支持的文件扩展名 */
    private static final Set<String> SUPPORTED = Set.of("md", "docx", "doc", "txt");

    /** 单文件大小上限：2MB */
    private static final long MAX_FILE_BYTES = 2 * 1024 * 1024L;

    /** 解析文本上限：20000 字符（与 ChatRequest.fileContent 的 @Size 一致） */
    private static final int MAX_TEXT_CHARS = 20000;

    @Override
    public FileParseResult parse(String originalFilename, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件内容为空");
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件不能超过 2MB");
        }
        String ext = extension(originalFilename);
        if (!SUPPORTED.contains(ext)) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "暂不支持该文件格式，仅支持 md / docx / doc / txt");
        }

        String text;
        try {
            switch (ext) {
                case "docx" -> text = parseDocx(bytes);
                case "doc" -> text = parseDoc(bytes);
                default -> text = parseText(bytes);
            }
        } catch (IOException e) {
            log.warn("文件解析失败：{}", e.getMessage());
            throw new BizException(ErrorCode.PARAM_ERROR, "文件解析失败，请确认文件未损坏");
        }

        if (!StringUtils.hasText(text)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未能从文件中提取到文本内容");
        }
        String trimmed = text.trim();
        int chars = trimmed.length();
        if (chars > MAX_TEXT_CHARS) {
            trimmed = trimmed.substring(0, MAX_TEXT_CHARS);
        }
        return new FileParseResult(originalFilename, Math.min(chars, MAX_TEXT_CHARS), trimmed);
    }

    private String extension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private String parseText(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String parseDocx(byte[] bytes) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                appendLine(sb, p.getText());
            }
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder line = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        if (!line.isEmpty()) {
                            line.append(" | ");
                        }
                        line.append(cell.getText().trim());
                    }
                    appendLine(sb, line.toString());
                }
            }
        }
        return sb.toString();
    }

    private String parseDoc(byte[] bytes) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (HWPFDocument doc = new HWPFDocument(new ByteArrayInputStream(bytes))) {
            try (WordExtractor extractor = new WordExtractor(doc)) {
                for (String para : extractor.getParagraphText()) {
                    appendLine(sb, para);
                }
            }
        }
        return sb.toString();
    }

    private void appendLine(StringBuilder sb, String line) {
        if (line != null && !line.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(line.trim());
        }
    }
}
