package com.sorts.ai.tool.support;

import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import com.sorts.common.result.Result;

/**
 * 内部调用结果解包。
 *
 * <p>「为什么要单独一层」：下游服务把业务错误统一表达为 HTTP 200 + {@code code≠0}，
 * 若不检查 code，Feign 不会报错，坏数据会静默流进 AI 上下文，
 * 最后表现为「AI 一本正经地胡说」。这里把 code≠0 显式转为异常，让问题在边界处暴露。</p>
 *
 * @author sorts
 */
public final class Results {

    private Results() {
    }

    public static <T> T unwrap(String action, Result<T> result) {
        if (result == null) {
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, action + "失败：依赖服务未返回数据");
        }
        if (result.getCode() != ErrorCode.SUCCESS.getCode()) {
            throw new BizException(result.getCode(), action + "失败：" + result.getMessage());
        }
        return result.getData();
    }
}
