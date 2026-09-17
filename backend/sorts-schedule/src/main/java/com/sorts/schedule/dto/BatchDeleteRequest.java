package com.sorts.schedule.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量删除请求（织程多选删除）。
 *
 * <p>用 POST + body 而不是 DELETE + body：fetch/axios 对带 body 的 DELETE
 * 兼容性差，且与既有 {@code POST /batch}（批量创建）风格一致。</p>
 *
 * @author sorts
 */
@Data
public class BatchDeleteRequest {

    @NotEmpty(message = "请选择要删除的日程")
    @Size(max = 200, message = "单次最多删除 200 条")
    private List<Long> ids;
}
