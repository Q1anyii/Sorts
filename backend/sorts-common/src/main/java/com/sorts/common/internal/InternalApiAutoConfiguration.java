package com.sorts.common.internal;

import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 内部接口凭证自动装配。
 *
 * <p>各服务引入 sorts-common 后即获得两端能力：入站校验（MVC 拦截器）
 * 与出站携带（Feign 拦截器），无需在各自服务里重复配置。</p>
 *
 * @author sorts
 */
@AutoConfiguration
@EnableConfigurationProperties(InternalTokenProperties.class)
public class InternalApiAutoConfiguration {

    /** MVC 拦截器：仅 Servlet 技术栈服务需要（网关为 WebFlux，自动跳过） */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(WebMvcConfigurer.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class InternalWebMvcConfiguration implements WebMvcConfigurer {

        private final InternalTokenProperties properties;

        InternalWebMvcConfiguration(InternalTokenProperties properties) {
            this.properties = properties;
        }

        @Bean
        @ConditionalOnMissingBean
        InternalApiInterceptor internalApiInterceptor() {
            return new InternalApiInterceptor(properties);
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(internalApiInterceptor());
        }
    }

    /** Feign 出站拦截器：没有 openfeign 的服务不创建（网关、common 自身单测） */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RequestInterceptor.class)
    static class InternalFeignConfiguration {

        @Bean
        @ConditionalOnMissingBean
        RequestInterceptor internalFeignInterceptor(InternalTokenProperties properties) {
            return new InternalFeignInterceptor(properties);
        }
    }
}
