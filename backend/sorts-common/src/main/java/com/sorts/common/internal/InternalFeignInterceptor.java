package com.sorts.common.internal;

import com.sorts.common.constant.AuthConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * Feign 出站拦截器（调用方一侧）：对白名单路径自动附加内部凭证。
 *
 * <p>把它做成自动装配的 Bean，业务代码就再也不需要记得「这个调用要带内部令牌」——
 * 需要人记住的安全约定，迟早会漏。</p>
 *
 * @author sorts
 */
@RequiredArgsConstructor
public class InternalFeignInterceptor implements RequestInterceptor {

    private final InternalTokenProperties properties;

    @Override
    public void apply(RequestTemplate template) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getToken())) {
            return;
        }
        String path = template.path();
        if (!StringUtils.hasText(path) || !matches(path)) {
            return;
        }
        // 已经显式带过的不要覆盖，方便个别调用点做特殊处理
        if (!template.headers().containsKey(AuthConstants.HEADER_INTERNAL_TOKEN)) {
            template.header(AuthConstants.HEADER_INTERNAL_TOKEN, properties.getToken());
        }
    }

    private boolean matches(String path) {
        return properties.getPaths().stream()
                .filter(StringUtils::hasText)
                .anyMatch(prefix -> path.startsWith(prefix) || path.contains(prefix));
    }
}
