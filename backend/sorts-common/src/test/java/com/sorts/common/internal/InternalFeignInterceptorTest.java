package com.sorts.common.internal;

import com.sorts.common.constant.AuthConstants;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feign 出站凭证拦截器单测。
 *
 * @author sorts
 */
@DisplayName("InternalFeignInterceptor 出站凭证携带")
class InternalFeignInterceptorTest {

    private InternalTokenProperties properties;

    private InternalFeignInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new InternalTokenProperties();
        properties.setToken("unit-test-token");
        interceptor = new InternalFeignInterceptor(properties);
    }

    @Test
    @DisplayName("白名单路径自动附加内部凭证")
    void internalPathGetsToken() {
        RequestTemplate template = template("/internal/schedules/upcoming");
        interceptor.apply(template);
        assertThat(template.headers().get(AuthConstants.HEADER_INTERNAL_TOKEN))
                .containsExactly("unit-test-token");
    }

    @Test
    @DisplayName("带上下文前缀的内部路径同样命中")
    void contextPathStillMatches() {
        RequestTemplate template = template("/api/v1/users/points/change");
        interceptor.apply(template);
        assertThat(template.headers()).containsKey(AuthConstants.HEADER_INTERNAL_TOKEN);
    }

    @Test
    @DisplayName("普通业务路径不携带凭证，避免凭证四处扩散")
    void normalPathUntouched() {
        RequestTemplate template = template("/api/v1/schedules");
        interceptor.apply(template);
        assertThat(template.headers()).doesNotContainKey(AuthConstants.HEADER_INTERNAL_TOKEN);
    }

    @Test
    @DisplayName("调用方已显式设置时不覆盖")
    void existingHeaderKept() {
        RequestTemplate template = template("/internal/demo");
        template.header(AuthConstants.HEADER_INTERNAL_TOKEN, "explicit");
        interceptor.apply(template);
        assertThat(template.headers().get(AuthConstants.HEADER_INTERNAL_TOKEN)).containsExactly("explicit");
    }

    @Test
    @DisplayName("关闭开关或未配置凭证时不附加")
    void disabledOrBlankSkips() {
        properties.setEnabled(false);
        assertThat(headersAfterApply("/internal/demo")).doesNotContainKey(AuthConstants.HEADER_INTERNAL_TOKEN);

        properties.setEnabled(true);
        properties.setToken("");
        assertThat(headersAfterApply("/internal/demo")).doesNotContainKey(AuthConstants.HEADER_INTERNAL_TOKEN);
    }

    private Map<String, Collection<String>> headersAfterApply(String path) {
        RequestTemplate template = template(path);
        interceptor.apply(template);
        return template.headers();
    }

    /** 构造真实 RequestTemplate，避免 mock 掩盖 Feign 真实行为 */
    private RequestTemplate template(String path) {
        RequestTemplate template = new RequestTemplate();
        template.uri(path);
        // uri() 会解析出 path，这里做一次断言兜底，防止 Feign 升级后本测试静默失效
        assertThat(template.path()).isEqualTo(path);
        // 保证 header 容器可写（Feign 默认惰性创建）
        template.headers(new HashMap<>());
        return template;
    }
}
