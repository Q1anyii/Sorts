package com.sorts.ai.controller;

import com.sorts.ai.service.FileParseService;
import com.sorts.ai.service.dto.FileParseResult;
import com.sorts.common.constant.AuthConstants;
import com.sorts.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件解析接口：AI 聊天界面上传文件（md / docx / doc / txt），
 * 解析为纯文本作为本次对话的临时记忆，仅用于提取计划，不落库。
 *
 * @author sorts
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/files")
@RequiredArgsConstructor
public class FileParseController {

    private final FileParseService fileParseService;

    @PostMapping("/parse")
    public Result<FileParseResult> parse(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId,
                                         @RequestParam("file") MultipartFile file) {
        try {
            FileParseResult result = fileParseService.parse(file.getOriginalFilename(), file.getBytes());
            return Result.success(result);
        } catch (java.io.IOException e) {
            log.warn("读取上传文件失败：{}", e.getMessage());
            throw new com.sorts.common.exception.BizException(
                    com.sorts.common.result.ErrorCode.PARAM_ERROR, "文件读取失败，请重新上传");
        }
    }
}
