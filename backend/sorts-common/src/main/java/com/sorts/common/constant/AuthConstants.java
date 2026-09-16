package com.sorts.common.constant;

/**
 * 认证链路通用常量：网关与业务服务共用，避免硬编码字符串。
 *
 * @author sorts
 */
public final class AuthConstants {

    private AuthConstants() {
    }

    /** 请求头：Authorization: Bearer &lt;token&gt; */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    /** 令牌前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 网关校验通过后，透传给下游服务的用户 ID 请求头 */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 网关透传的用户名（便于日志追踪） */
    public static final String HEADER_USERNAME = "X-Username";

    /** JWT 载荷：令牌类型 */
    public static final String CLAIM_TYPE = "type";

    /** JWT 载荷：用户名 */
    public static final String CLAIM_USERNAME = "username";

    /** 令牌类型：访问令牌（短时效，访问业务接口） */
    public static final String TYPE_ACCESS = "access";

    /** 令牌类型：刷新令牌（长时效，仅用于换取新 access token） */
    public static final String TYPE_REFRESH = "refresh";
}
