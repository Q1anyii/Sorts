package com.sorts.ai.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量删除请求（织史 / AI 会话多选删除共用）。
 *
 * @author sorts
 */
@Data
public class BatchDeleteRequest {

    @NotEmpty(message = "请选择要删除的数据")
    @Size(max = 200, message = "单次最多删除 200 条")
    private List<Long> ids;
}
