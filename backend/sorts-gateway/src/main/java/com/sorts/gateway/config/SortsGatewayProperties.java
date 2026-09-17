package com.sorts.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关可配置属性（前缀 sorts.gateway）。
 *
 * 注意类名：不能叫 GatewayProperties —— Spring Cloud Gateway 自带一个同名配置类，
 * 其自动配置会注册 bean 名 `gatewayProperties`，与本类 @Component 的默认 bean 名相同，
 * 在 Boot 默认（禁止 bean 覆盖）下会导致网关**直接启动失败**。
 *
 * @author sorts
 */
@Data
@Component
@ConfigurationProperties(prefix = "sorts.gateway")
public class SortsGatewayProperties {

    /**
     * 放行路径（Ant 风格）：登录注册、令牌刷新、健康检查、接口文档等。
     * 支持配置文件覆盖，新增公开接口时无需改动代码。
     */
    private List<String> whitelist = new ArrayList<>(List.of(
            "/api/v1/auth/**",
            "/api/v1/users/avatar/files/**",
            "/actuator/**",
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/favicon.ico",
            "/error"
    ));
}
