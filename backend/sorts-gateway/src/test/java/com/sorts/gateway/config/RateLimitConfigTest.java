package com.sorts.gateway.config;

import com.sorts.common.constant.AuthConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 限流 KeyResolver 单元测试：验证限流维度选择（登录按用户、未登录按 IP）。
 *
 * @author sorts
 */
class RateLimitConfigTest {

    private final KeyResolver keyResolver = new RateLimitConfig().userKeyResolver();

    private String resolveKey(MockServerWebExchange exchange) {
        return keyResolver.resolve(exchange).block();
    }

    @Test
    @DisplayName("已登录用户按用户维度限流")
    void shouldResolveUserKey() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/schedules")
                        .header(AuthConstants.HEADER_USER_ID, "7")
                        .build());

        assertEquals("rate:user:7", resolveKey(exchange));
    }

    @Test
    @DisplayName("未登录请求按客户端 IP 限流")
    void shouldResolveIpKeyWhenAnonymous() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login")
                        .remoteAddress(new InetSocketAddress("10.0.0.5", 51234))
                        .build());

        assertEquals("rate:ip:10.0.0.5", resolveKey(exchange));
    }

    @Test
    @DisplayName("经过代理时优先使用 X-Forwarded-For 的首个地址")
    void shouldPreferForwardedFor() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login")
                        .header("X-Forwarded-For", "203.0.113.9, 10.0.0.5")
                        .remoteAddress(new InetSocketAddress("10.0.0.5", 51234))
                        .build());

        assertEquals("rate:ip:203.0.113.9", resolveKey(exchange));
    }

    @Test
    @DisplayName("取不到地址时归入 unknown，保证限流 key 非空")
    void shouldFallbackToUnknown() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login").build());

        assertEquals("rate:ip:unknown", resolveKey(exchange));
    }

    @Test
    @DisplayName("X-User-Id 存在时不再按 IP 限流（多端共享同一额度）")
    void userHeaderShouldTakePrecedenceOverIp() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/schedules")
                        .header(AuthConstants.HEADER_USER_ID, "1001")
                        .header("X-Forwarded-For", "203.0.113.9")
                        .build());

        assertEquals("rate:user:1001", resolveKey(exchange));
    }
}
