package com.sorts.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sorts.common.constant.AuthConstants;
import com.sorts.common.exception.BizException;
import com.sorts.common.jwt.JwtUtil;
import com.sorts.common.result.ErrorCode;
import com.sorts.common.result.Result;
import com.sorts.gateway.config.SortsGatewayProperties;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关全局鉴权过滤器。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>白名单与预检请求直接放行；</li>
 *   <li>校验 access token 的合法性与类型，过期/非法返回 401 统一响应；</li>
 *   <li>校验通过后把用户身份写入内部请求头，透传给下游微服务；</li>
 *   <li>剥离外部伪造的 X-User-Id / X-Username，防止身份伪造。</li>
 * </ul>
 *
 * @author sorts
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtUtil jwtUtil;

    private final SortsGatewayProperties gatewayProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 跨域预检请求直接放行；白名单同理，但两者都要剥离外部伪造的内部凭证
        if (HttpMethod.OPTIONS.equals(request.getMethod()) || isWhitelisted(path)) {
            return chain.filter(exchange.mutate().request(stripInternalToken(request)).build());
        }

        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            return writeUnauthorized(exchange, ErrorCode.UNAUTHORIZED);
        }

        final Long userId;
        final String username;
        try {
            Claims claims = jwtUtil.parse(token, AuthConstants.TYPE_ACCESS);
            userId = Long.valueOf(claims.getSubject());
            username = claims.get(AuthConstants.CLAIM_USERNAME, String.class);
        } catch (BizException e) {
            ErrorCode errorCode = e.getCode() == ErrorCode.TOKEN_EXPIRED.getCode()
                    ? ErrorCode.TOKEN_EXPIRED
                    : ErrorCode.TOKEN_INVALID;
            return writeUnauthorized(exchange, errorCode);
        } catch (NumberFormatException e) {
            return writeUnauthorized(exchange, ErrorCode.TOKEN_INVALID);
        }

        // 透传身份前先移除外部可能伪造的身份头与内部凭证
        ServerHttpRequest mutated = request.mutate()
                .headers(headers -> {
                    headers.remove(AuthConstants.HEADER_USER_ID);
                    headers.remove(AuthConstants.HEADER_USERNAME);
                    headers.remove(AuthConstants.HEADER_INTERNAL_TOKEN);
                    headers.set(AuthConstants.HEADER_USER_ID, String.valueOf(userId));
                    if (StringUtils.hasText(username)) {
                        headers.set(AuthConstants.HEADER_USERNAME, username);
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /**
     * 剥离外部请求携带的内部凭证。
     *
     * <p>服务间调用不经过网关，因此经网关进来的 {@code X-Internal-Token} 只可能是伪造的。
     * 若放任其透传，任何用户都能越权调用内部接口。</p>
     */
    private ServerHttpRequest stripInternalToken(ServerHttpRequest request) {
        if (!request.getHeaders().containsKey(AuthConstants.HEADER_INTERNAL_TOKEN)) {
            return request;
        }
        return request.mutate()
                .headers(headers -> headers.remove(AuthConstants.HEADER_INTERNAL_TOKEN))
                .build();
    }

    /** 优先取 Authorization 头，兼容表单/移动端通过 access_token 参数传递 */
    private String resolveToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(AuthConstants.HEADER_AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith(AuthConstants.TOKEN_PREFIX)) {
            return authorization.substring(AuthConstants.TOKEN_PREFIX.length());
        }
        return request.getQueryParams().getFirst("access_token");
    }

    private boolean isWhitelisted(String path) {
        return gatewayProperties.getWhitelist().stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    /** 以统一响应体写出 401 + JSON，保证前端拿到结构化错误信息 */
    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, ErrorCode errorCode) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            byte[] body = OBJECT_MAPPER.writeValueAsString(Result.fail(errorCode))
                    .getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        } catch (JsonProcessingException e) {
            log.error("序列化鉴权失败响应异常", e);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }
    }

    @Override
    public int getOrder() {
        // 需早于路由转发执行
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
