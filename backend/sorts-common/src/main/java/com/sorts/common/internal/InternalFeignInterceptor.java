package com.sorts.common.internal;

import com.sorts.common.constant.AuthConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * Feign 出站拦截器（调用方一侧）：对白名单路径自动附加内部凭证。
 *
 * <p>把它做成自动装配的 Bean，业务代码就再也不需要记得「这个调用要带内部令牌」——
 * 需要人记住的安全约定，迟早会漏。</p>
 *
 * @author sorts
 */
@Slf4j
@RequiredArgsConstructor
public class InternalFeignInterceptor implements RequestInterceptor {

    private final InternalTokenProperties properties;

    @Override
    public void apply(RequestTemplate template) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getToken())) {
            log.debug("[internal-feign] 跳过附加凭证：enabled={}, hasToken={}",
                    properties.isEnabled(), StringUtils.hasText(properties.getToken()));
            return;
        }
        if (!matches(template)) {
            log.debug("[internal-feign] 路径不在白名单，不附加凭证：path={}, url={}, target={}",
                    template.path(), template.url(), describeTarget(template));
            return;
        }
        // 已经显式带过的不要覆盖，方便个别调用点做特殊处理
        if (!template.headers().containsKey(AuthConstants.HEADER_INTERNAL_TOKEN)) {
            template.header(AuthConstants.HEADER_INTERNAL_TOKEN, properties.getToken());
            log.debug("[internal-feign] 已为 path={} 附加内部凭证", template.path());
        }
    }

    private String describeTarget(RequestTemplate template) {
        try {
            feign.Target<?> target = template.feignTarget();
            if (target == null) {
                return "null";
            }
            return "url=" + target.url() + ", name=" + target.name();
        } catch (Exception e) {
            return "err:" + e.getMessage();
        }
    }

    /**
     * 白名单匹配：同时看方法级路径与完整 URL。
     *
     * <p>坑：{@code @FeignClient(path = "/api/v1/users")} 的前缀不会出现在
     * {@link RequestTemplate#path()} 里（只返回方法注解路径，如 {@code /points/change}），
     * 仅按 path 判断会把这类调用误判为「不在白名单」，导致内部凭证从不附加、
     * 对端 {@link InternalApiInterceptor} 一律 403。Target 的完整 URL 含 client path，兜住该场景。</p>
     */
    private boolean matches(RequestTemplate template) {
        final String path = template.path();
        final String url = template.url();
        final String targetUrl = resolveTargetUrl(template);
        // Target 的完整 URL 只有 client path（如 http://sorts-user/api/v1/users），
        // 还需拼上方法级路径（/points/change）才是白名单要匹配的完整路径
        final String fullTargetUrl = joinPath(targetUrl, path);
        return properties.getPaths().stream()
                .filter(StringUtils::hasText)
                .anyMatch(prefix ->
                        (StringUtils.hasText(path) && (path.startsWith(prefix) || path.contains(prefix)))
                                || (StringUtils.hasText(url) && url.contains(prefix))
                                || (StringUtils.hasText(fullTargetUrl) && fullTargetUrl.contains(prefix)));
    }

    /** 拼接基础 URL 与方法路径，兼容斜杠缺失/重复 */
    private String joinPath(String base, String tail) {
        if (!StringUtils.hasText(base)) {
            return tail;
        }
        if (!StringUtils.hasText(tail)) {
            return base;
        }
        return base.replaceAll("/+$", "") + "/" + tail.replaceAll("^/+", "");
    }

    /** Target 的完整 URL 含 @FeignClient path 前缀；拿不到时回落 null */
    private String resolveTargetUrl(RequestTemplate template) {
        try {
            feign.Target<?> target = template.feignTarget();
            if (target == null) {
                return null;
            }
            return target.url();
        } catch (Exception ignore) {
            // 个别实现可能没有 target，回落 path/url 判断
            return null;
        }
    }
}
