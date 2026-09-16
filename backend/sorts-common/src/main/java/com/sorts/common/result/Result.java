package com.sorts.common.result;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 全平台统一响应体。
 *
 * <pre>
 * { "code": 0, "message": "success", "data": {...}, "timestamp": 1710000000000 }
 * </pre>
 *
 * @author sorts
 */
@Data
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码，0 表示成功 */
    private int code;

    /** 提示信息，可直接展示给用户 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 响应时间戳（毫秒） */
    private long timestamp = System.currentTimeMillis();

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ErrorCode.SUCCESS.getCode());
        result.setMessage(ErrorCode.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    /** 支持自定义覆盖 message（例如参数校验失败时给出具体字段说明） */
    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return fail(errorCode.getCode(), message);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
