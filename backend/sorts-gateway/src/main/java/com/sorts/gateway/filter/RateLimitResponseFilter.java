package com.sorts.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sorts.common.result.ErrorCode;
import com.sorts.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 限流响应统一化过滤器。
 *
 * <p>Spring Cloud Gateway 的 {@code RequestRateLimiter} 被触发时只返回状态码 429 与空响应体，
 * 前端拿不到结构化信息。此过滤器把 429 改写为平台统一响应体（含 code / message），
 * 同时保留限流器写入的 X-RateLimit-* 响应头。</p>
 *
 * <p>实现要点：以「响应装饰器」包住下游写入动作，因此必须在限流器之前执行（order 更小）。</p>
 *
 * @author sorts
 */
@Slf4j
@Component
public class RateLimitResponseFilter implements GlobalFilter, Ordered {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 预序列化 429 响应体，避免每次限流都做 JSON 序列化 */
    private static final byte[] RATE_LIMITED_BODY = buildBody();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponseDecorator decorator = new ServerHttpResponseDecorator(exchange.getResponse()) {

            @Override
            public Mono<Void> setComplete() {
                return isRateLimited() ? writeRateLimitedBody() : super.setComplete();
            }

            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                return isRateLimited() ? writeRateLimitedBody() : super.writeWith(body);
            }

            private boolean isRateLimited() {
                return HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode());
            }

            private Mono<Void> writeRateLimitedBody() {
                getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return super.writeWith(Mono.just(bufferFactory().wrap(RATE_LIMITED_BODY)));
            }
        };
        return chain.filter(exchange.mutate().response(decorator).build());
    }

    private static byte[] buildBody() {
        try {
            return OBJECT_MAPPER.writeValueAsString(Result.fail(ErrorCode.RATE_LIMITED))
                    .getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            // Result 结构固定，序列化理论上不会失败；此处仅作兜底
            log.warn("429 响应体预序列化失败，降级为固定文本", e);
            return "{\"code\":429,\"message\":\"操作过于频繁，请稍后再试\"}".getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public int getOrder() {
        // 早于鉴权之后的限流器执行，保证装饰器已经包住响应
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
