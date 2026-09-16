package com.sorts.common.internal;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.exception.BizException;
import com.sorts.common.result.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部接口凭证校验拦截器（服务端一侧）。
 *
 * <p>只对标注了 {@link InternalApi} 的处理器生效，其余接口不受影响；
 * 未配置凭证时<b>拒绝放行</b>（fail-closed）——配置缺失不应该变成「默认敞开」。</p>
 *
 * @author sorts
 */
@Slf4j
@RequiredArgsConstructor
public class InternalApiInterceptor implements HandlerInterceptor {

    private final InternalTokenProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod) || !properties.isEnabled()) {
            return true;
        }
        if (!isInternal(handlerMethod)) {
            return true;
        }
        if (!StringUtils.hasText(properties.getToken())) {
            log.error("内部接口 {} 被访问，但未配置 sorts.internal.token，已拒绝", request.getRequestURI());
            throw new BizException(ErrorCode.FORBIDDEN, "内部接口未配置服务间凭证");
        }
        String provided = request.getHeader(AuthConstants.HEADER_INTERNAL_TOKEN);
        if (provided == null || !constantTimeEquals(provided, properties.getToken())) {
            log.warn("内部接口 {} 凭证校验失败", request.getRequestURI());
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该资源");
        }
        return true;
    }

    private boolean isInternal(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), InternalApi.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), InternalApi.class);
    }

    /** 定长比较，避免通过响应耗时逐字节试探凭证 */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
