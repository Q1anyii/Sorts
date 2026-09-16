package com.sorts.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码定义。
 *
 * <p>约定：前三位对齐 HTTP 语义，后两位作为业务细分（例如 40101 表示令牌过期）。
 * 业务模块自定义错误码从 20000 起，避免与通用码冲突。</p>
 *
 * @author sorts
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "success"),

    // ========== 40x 客户端错误 ==========
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录状态已失效"),
    TOKEN_EXPIRED(40101, "访问令牌已过期"),
    TOKEN_INVALID(40102, "令牌非法"),
    REFRESH_TOKEN_INVALID(40103, "刷新令牌无效，请重新登录"),
    FORBIDDEN(403, "无权访问该资源"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方式不被允许"),
    CONFLICT(409, "资源状态冲突"),

    // ========== 42x 流量控制 ==========
    RATE_LIMITED(429, "操作过于频繁，请稍后再试"),

    // ========== 50x 服务端错误 ==========
    SYSTEM_ERROR(500, "系统繁忙，请稍后再试"),
    SERVICE_UNAVAILABLE(503, "依赖服务暂时不可用");

    private final int code;

    private final String message;
}
