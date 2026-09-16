package com.sorts.gateway.filter;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.jwt.JwtProperties;
import com.sorts.common.jwt.JwtUtil;
import com.sorts.common.result.ErrorCode;
import com.sorts.common.result.Result;
import com.sorts.gateway.config.SortsGatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关鉴权过滤器单元测试。
 *
 * <p>覆盖：白名单放行、预检放行、缺失/非法/过期令牌拒绝、
 * 令牌类型校验、身份透传、伪造身份头与内部凭证剥离。</p>
 *
 * @author sorts
 */
class AuthGlobalFilterTest {

    private static final String SECRET = "sorts-gateway-test-secret-key-0123456789abcdef";

    private JwtUtil jwtUtil;

    private AuthGlobalFilter authGlobalFilter;

    private SortsGatewayProperties gatewayProperties;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setAccessExpireMinutes(30L);
        jwtUtil = new JwtUtil(properties);
        gatewayProperties = new SortsGatewayProperties();
        authGlobalFilter = new AuthGlobalFilter(jwtUtil, gatewayProperties);
    }

    /** 记录链路是否被放行，并捕获透传给下游的请求头 */
    private static final class RecordingChain implements GatewayFilterChain {

        private boolean invoked;

        private final AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            this.invoked = true;
            this.captured.set(exchange);
            return Mono.empty();
        }
    }

    private String bodyOf(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }

    @Test
    @DisplayName("白名单路径直接放行，不解析令牌")
    void shouldPassWhitelistedPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build());
        RecordingChain chain = new RecordingChain();

        authGlobalFilter.filter(exchange, chain).block();

        assertTrue(chain.invoked, "白名单请求应进入下游链路");
    }

    @Test
    @DisplayName("跨域预检请求直接放行")
    void shouldPassPreflightRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/v1/schedules").build());
        RecordingChain chain = new RecordingChain();

        authGlobalFilter.filter(exchange, chain).block();

        assertTrue(chain.invoked, "OPTIONS 请求应放行");
    }

    @Test
    @DisplayName("缺失令牌返回 401 + 统一响应体")
    void shouldRejectMissingToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me").build());
        RecordingChain chain = new RecordingChain();

        authGlobalFilter.filter(exchange, chain).block();

        assertFalse(chain.invoked, "无令牌不应进入下游");
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertTrue(bodyOf(exchange).contains(String.valueOf(ErrorCode.UNAUTHORIZED.getCode())));
    }

    @Test
    @DisplayName("非法令牌返回 401 (TOKEN_INVALID)")
    void shouldRejectInvalidToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(AuthConstants.HEADER_AUTHORIZATION, AuthConstants.TOKEN_PREFIX + "garbage")
                        .build());
        RecordingChain chain = new RecordingChain();

        authGlobalFilter.filter(exchange, chain).block();

        assertFalse(chain.invoked);
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertTrue(bodyOf(exchange).contains(String.valueOf(ErrorCode.TOKEN_INVALID.getCode())));
    }

    @Test
    @DisplayName("过期令牌返回 401 (TOKEN_EXPIRED)，便于前端触发无感续期")
    void shouldRejectExpiredToken() {
        JwtProperties expiredProps = new JwtProperties();
        expiredProps.setSecret(SECRET);
        expiredProps.setAccessExpireMinutes(-1L);
        String expiredToken = new JwtUtil(expiredProps).generateAccessToken(7L, "weaver");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(AuthConstants.HEADER_AUTHORIZATION, AuthConstants.TOKEN_PREFIX + expiredToken)
                        .build());
        RecordingChain chain = new RecordingChain();

        authGlobalFilter.filter(exchange, chain).block();

        assertFalse(chain.invoked);
        assertTrue(bodyOf(exchange).contains(String.valueOf(ErrorCode.TOKEN_EXPIRED.getCode())));
    }

    @Test
    @DisplayName("refresh token 不可用于访问业务接口")
    void shouldRejectRefreshTokenAsAccessToken() {
        String refreshToken = jwtUtil.generateRefreshToken(7L);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(AuthConstants.HEADER_AUTHORIZATION, AuthConstants.TOKEN_PREFIX + refreshToken)
                        .build());
        RecordingChain chain = new RecordingChain();

        authGlobalFilter.filter(exchange, chain).block();

        assertFalse(chain.invoked, "refresh token 不应通过鉴权");
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("合法令牌放行并透传用户身份")
    void shouldPassAndInjectUserIdHeader() {
        String accessToken = jwtUtil.generateAccessToken(1001L, "weaver");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(AuthConstants.HEADER_AUTHORIZATION, AuthConstants.TOKEN_PREFIX + accessToken)
                        .build());
        RecordingChain chain = new RecordingChain();

        authGlobalFilter.filter(exchange, chain).block();

        assertTrue(chain.invoked);
        ServerWebExchange downstream = chain.captured.get();
        assertNotNull(downstream);
        assertEquals("1001", downstream.getRequest().getHeaders().getFirst(AuthConstants.HEADER_USER_ID));
        assertEquals("weaver", downstream.getRequest().getHeaders().getFirst(AuthConstants.HEADER_USERNAME));
    }

    @Test
    @DisplayName("伪造的 X-User-Id 被剥离，防止越权")
    void shouldStripForgedIdentityHeaders() {
        String accessToken = jwtUtil.generateAccessToken(1001L, "weaver");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(AuthConstants.HEADER_AUTHORIZATION, AuthConstants.TOKEN_PREFIX + accessToken)
                        // 攻击者伪造的身份头
                        .header(AuthConstants.HEADER_USER_ID, "999")
                        .header(AuthConstants.HEADER_USERNAME, "admin")
                        .build());
        RecordingChain chain = new RecordingChain();

        authGlobalFilter.filter(exchange, chain).block();

        ServerWebExchange downstream = chain.captured.get();
        assertNotNull(downstream);
        // 只保留网关写入的真实身份，且同名头不会重复注入
        assertEquals("1001", downstream.getRequest().getHeaders().getFirst(AuthConstants.HEADER_USER_ID));
        assertEquals(1, downstream.getRequest().getHeaders().get(AuthConstants.HEADER_USER_ID).size());
        assertEquals("weaver", downstream.getRequest().getHeaders().getFirst(AuthConstants.HEADER_USERNAME));
    }

    @Test
    @DisplayName("伪造的 X-Internal-Token 被剥离，无法越权调用内部接口")
    void shouldStripForgedInternalToken() {
        String accessToken = jwtUtil.generateAccessToken(1001L, "weaver");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/points/change")
                        .header(AuthConstants.HEADER_AUTHORIZATION, AuthConstants.TOKEN_PREFIX + accessToken)
                        // 外部请求不该携带内部凭证，带了就一定是伪造的
                        .header(AuthConstants.HEADER_INTERNAL_TOKEN, "forged-token")
                        .build());
        RecordingChain chain = new RecordingChain();

        authGlobalFilter.filter(exchange, chain).block();

        ServerWebExchange downstream = chain.captured.get();
        assertNotNull(downstream);
        assertNull(downstream.getRequest().getHeaders().getFirst(AuthConstants.HEADER_INTERNAL_TOKEN));
    }

    @Test
    @DisplayName("白名单路径同样剥离伪造的内部凭证（放行不等于放行一切）")
    void shouldStripInternalTokenOnWhitelistedPath() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .header(AuthConstants.HEADER_INTERNAL_TOKEN, "forged-token")
                        .build());
        RecordingChain chain = new RecordingChain();

        authGlobalFilter.filter(exchange, chain).block();

        assertTrue(chain.invoked, "白名单请求应进入下游链路");
        assertNull(chain.captured.get().getRequest().getHeaders().getFirst(AuthConstants.HEADER_INTERNAL_TOKEN));
    }

    @Test
    @DisplayName("兼容通过 access_token 查询参数传递令牌")
    void shouldResolveTokenFromQueryParam() {
        String accessToken = jwtUtil.generateAccessToken(2002L, "shuttle");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/schedules?access_token=" + accessToken).build());
        RecordingChain chain = new RecordingChain();

        authGlobalFilter.filter(exchange, chain).block();

        assertTrue(chain.invoked);
        assertEquals("2002",
                chain.captured.get().getRequest().getHeaders().getFirst(AuthConstants.HEADER_USER_ID));
    }

    @Test
    @DisplayName("统一错误响应体可被反序列化为 Result")
    void errorBodyShouldBeDeserializable() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me").build());

        authGlobalFilter.filter(exchange, new RecordingChain()).block();

        String body = bodyOf(exchange);
        Result<?> result = new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, Result.class);
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), result.getCode());
        assertNotNull(result.getMessage());
    }
}
