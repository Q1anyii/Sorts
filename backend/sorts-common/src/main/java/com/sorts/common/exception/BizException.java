package com.sorts.common.exception;

import com.sorts.common.result.ErrorCode;
import lombok.Getter;

/**
 * 业务异常：可被全局异常处理器识别并转换为统一响应体。
 *
 * <p>使用方式：throw new BizException(ErrorCode.NOT_FOUND) 或带自定义描述。</p>
 *
 * @author sorts
 */
@Getter
public class BizException extends RuntimeException {

    /** 业务错误码 */
    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
