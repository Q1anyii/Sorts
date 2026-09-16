package com.sorts.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关可配置属性（前缀 sorts.gateway）。
 *
 * @author sorts
 */
@Data
@Component
@ConfigurationProperties(prefix = "sorts.gateway")
public class GatewayProperties {

    /**
     * 放行路径（Ant 风格）：登录注册、令牌刷新、健康检查、接口文档等。
     * 支持配置文件覆盖，新增公开接口时无需改动代码。
     */
    private List<String> whitelist = new ArrayList<>(List.of(
            "/api/v1/auth/**",
            "/actuator/**",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/favicon.ico",
            "/error"
    ));
}
