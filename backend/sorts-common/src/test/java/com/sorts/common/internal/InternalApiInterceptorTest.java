package com.sorts.common.internal;

import com.sorts.common.constant.AuthConstants;
import com.sorts.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内部接口凭证拦截器单测。
 *
 * @author sorts
 */
@DisplayName("InternalApiInterceptor 内部接口凭证校验")
class InternalApiInterceptorTest {

    private InternalTokenProperties properties;

    private InternalApiInterceptor interceptor;

    /** 测试用控制器：类上无注解，方法级别差异化标注 */
    static class SampleController {

        @InternalApi("跨用户取数")
        public void methodLevel() {
        }

        public void plain() {
        }
    }

    /** 测试用控制器：类级注解，整类生效 */
    @InternalApi
    static class WholeInternalController {

        public void anything() {
        }
    }

    @BeforeEach
    void setUp() {
        properties = new InternalTokenProperties();
        properties.setToken("unit-test-token");
        interceptor = new InternalApiInterceptor(properties);
    }

    @Test
    @DisplayName("未标注 @InternalApi 的处理器直接放行")
    void plainHandlerPassesThrough() throws Exception {
        assertThat(interceptor.preHandle(request(null), new MockHttpServletResponse(), handler("plain"))).isTrue();
    }

    @Test
    @DisplayName("标注 @InternalApi 且凭证正确时放行")
    void correctTokenPasses() throws Exception {
        assertThat(interceptor.preHandle(request("unit-test-token"), new MockHttpServletResponse(),
                handler("methodLevel"))).isTrue();
    }

    @Test
    @DisplayName("缺少凭证头时拒绝")
    void missingTokenRejected() throws Exception {
        assertThatThrownBy(() -> interceptor.preHandle(request(null), new MockHttpServletResponse(),
                handler("methodLevel")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    @DisplayName("凭证错误时拒绝，且不泄漏期望值")
    void wrongTokenRejected() throws Exception {
        assertThatThrownBy(() -> interceptor.preHandle(request("wrong"), new MockHttpServletResponse(),
                handler("methodLevel")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("类级注解对整个控制器生效")
    void classLevelAnnotationGuarded() throws Exception {
        Method method = WholeInternalController.class.getMethod("anything");
        assertThatThrownBy(() -> interceptor.preHandle(request(null), new MockHttpServletResponse(),
                new HandlerMethod(new WholeInternalController(), method)))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("非 HandlerMethod 处理器（静态资源等）不受影响")
    void nonHandlerMethodPasses() {
        assertThat(interceptor.preHandle(request(null), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    @DisplayName("总开关关闭时放行，便于本地排障")
    void disabledPassesThrough() throws Exception {
        properties.setEnabled(false);
        assertThat(interceptor.preHandle(request(null), new MockHttpServletResponse(), handler("methodLevel"))).isTrue();
    }

    @Test
    @DisplayName("未配置凭证时 fail-closed：拒绝而不是默认敞开")
    void blankConfiguredTokenFailsClosed() throws Exception {
        properties.setToken("  ");
        assertThatThrownBy(() -> interceptor.preHandle(request("  "), new MockHttpServletResponse(),
                handler("methodLevel")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("未配置");
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        SampleController bean = new SampleController();
        return new HandlerMethod(bean, SampleController.class.getMethod(methodName));
    }

    private HttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/internal/demo");
        if (token != null) {
            request.addHeader(AuthConstants.HEADER_INTERNAL_TOKEN, token);
        }
        return request;
    }
}
