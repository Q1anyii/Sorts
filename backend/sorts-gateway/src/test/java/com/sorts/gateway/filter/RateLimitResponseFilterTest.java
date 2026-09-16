package com.sorts.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sorts.common.result.ErrorCode;
import com.sorts.common.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 限流响应统一化过滤器单元测试。
 *
 * <p>覆盖：空体 429 被改写为统一响应体且保留限流响应头；正常响应不受影响。</p>
 *
 * @author sorts
 */
class RateLimitResponseFilterTest {

    private final RateLimitResponseFilter filter = new RateLimitResponseFilter();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockServerWebExchange exchangeOf(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }

    @Test
    @DisplayName("429 空响应体被改写为统一 JSON，并保留 X-RateLimit-* 头")
    void shouldRewriteRateLimitedResponse() throws Exception {
        MockServerWebExchange exchange = exchangeOf("/api/v1/schedules");

        // 模拟 RequestRateLimiter 的行为：设置 429、限流头，然后直接 complete（无 body）
        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            ex.getResponse().getHeaders().add("X-RateLimit-Remaining", "0");
            ex.getResponse().getHeaders().add("X-RateLimit-Requested-Tokens", "1");
            return ex.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("0", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));

        String body = exchange.getResponse().getBodyAsString().block();
        assertTrue(body != null && !body.isBlank(), "429 响应必须有结构化响应体");
        Result<?> result = objectMapper.readValue(body, Result.class);
        assertEquals(ErrorCode.RATE_LIMITED.getCode(), result.getCode());
        assertEquals(ErrorCode.RATE_LIMITED.getMessage(), result.getMessage());
    }

    @Test
    @DisplayName("429 带响应体时同样被统一化，避免出现两种 429 格式")
    void shouldRewriteRateLimitedResponseWithBody() throws Exception {
        MockServerWebExchange exchange = exchangeOf("/api/v1/schedules");

        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            DataBuffer buffer = ex.getResponse().bufferFactory()
                    .wrap("Too Many Requests".getBytes(StandardCharsets.UTF_8));
            return ex.getResponse().writeWith(Mono.just(buffer));
        };

        filter.filter(exchange, chain).block();

        String body = exchange.getResponse().getBodyAsString().block();
        Result<?> result = objectMapper.readValue(body, Result.class);
        assertEquals(ErrorCode.RATE_LIMITED.getCode(), result.getCode());
    }

    @Test
    @DisplayName("正常响应体原样透传，不被改写")
    void shouldKeepNormalResponseUntouched() {
        MockServerWebExchange exchange = exchangeOf("/api/v1/schedules");

        GatewayFilterChain chain = ex -> {
            ex.getResponse().setStatusCode(HttpStatus.OK);
            DataBuffer buffer = ex.getResponse().bufferFactory()
                    .wrap("{\"code\":0}".getBytes(StandardCharsets.UTF_8));
            return ex.getResponse().writeWith(Mono.just(buffer));
        };

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
        String body = exchange.getResponse().getBodyAsString().block();
        assertEquals("{\"code\":0}", body);
    }

    @Test
    @DisplayName("过滤器顺序早于限流器，保证装饰器先包住响应")
    void shouldRunBeforeRateLimiter() {
        assertTrue(filter.getOrder() < 0, "order 必须小于 0（限流器等路由过滤器默认 order 为 0）");
    }
}
