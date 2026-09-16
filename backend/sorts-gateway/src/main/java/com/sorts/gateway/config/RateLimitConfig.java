package com.sorts.gateway.config;

import com.sorts.common.constant.AuthConstants;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * 限流配置。
 *
 * <p>限流维度：</p>
 * <ul>
 *   <li>已登录（已通过鉴权过滤器写入 X-User-Id）→ 按用户维度限流，避免同一用户多端刷接口；</li>
 *   <li>未登录 → 按客户端 IP 限流，用于登录、注册等白名单接口防爆破。</li>
 * </ul>
 *
 * <p>令牌桶算法由 Spring Cloud Gateway 的 {@code RedisRateLimiter} 提供（Redis + Lua，分布式一致）。</p>
 *
 * @author sorts
 */
@Configuration
public class RateLimitConfig {

    /** 用户维度 key 前缀，便于在 Redis 中区分限流键 */
    private static final String USER_KEY_PREFIX = "rate:user:";

    private static final String IP_KEY_PREFIX = "rate:ip:";

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(AuthConstants.HEADER_USER_ID);
            if (StringUtils.hasText(userId)) {
                return Mono.just(USER_KEY_PREFIX + userId);
            }
            return Mono.just(IP_KEY_PREFIX + resolveClientIp(exchange));
        };
    }

    /** 依次尝试 X-Forwarded-For 与直连地址，取不到时归入 unknown，保证 key 永不为空 */
    private String resolveClientIp(org.springframework.web.server.ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null || remote.getAddress() == null
                ? "unknown"
                : remote.getAddress().getHostAddress();
    }
}
